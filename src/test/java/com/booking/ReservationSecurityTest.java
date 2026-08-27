package com.booking;

import com.booking.dto.reservation.ReservationRequest;
import com.booking.entity.*;
import com.booking.repository.ReservationRepository;
import com.booking.repository.ResourceRepository;
import com.booking.repository.UserRepository;
import com.booking.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;
    private String userAToken;
    private String userBToken;
    private User userA;
    private User userB;
    private Resource resource;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        resourceRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder().username("adminX").email("adminX@test.com")
                .password(passwordEncoder.encode("pw")).role(Role.ADMIN).enabled(true).build());
        userA = userRepository.save(User.builder().username("userA").email("userA@test.com")
                .password(passwordEncoder.encode("pw")).role(Role.USER).enabled(true).build());
        userB = userRepository.save(User.builder().username("userB").email("userB@test.com")
                .password(passwordEncoder.encode("pw")).role(Role.USER).enabled(true).build());

        adminToken = "Bearer " + jwtUtil.generateToken("adminX", "ADMIN");
        userAToken = "Bearer " + jwtUtil.generateToken("userA", "USER");
        userBToken = "Bearer " + jwtUtil.generateToken("userB", "USER");

        resource = resourceRepository.save(Resource.builder()
                .name("Room 1").capacity(10).available(true).build());
    }

    private Reservation createReservationFor(User user) {
        return reservationRepository.save(Reservation.builder()
                .resource(resource)
                .user(user)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .price(new BigDecimal("50.00"))
                .status(ReservationStatus.PENDING)
                .build());
    }

    @Test
    void createReservation_ownerIsAlwaysTakenFromJwt_notRequestBody() throws Exception {
        ReservationRequest request = new ReservationRequest(
                resource.getId(),
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(1),
                new BigDecimal("99.99"));

        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("userA"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createReservation_withEndBeforeStart_returnsBadRequest() throws Exception {
        ReservationRequest request = new ReservationRequest(
                resource.getId(),
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(1), // before start -> invalid
                new BigDecimal("10.00"));

        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void user_cannotViewAnotherUsersReservation() throws Exception {
        Reservation reservationOfA = createReservationFor(userA);

        mockMvc.perform(get("/api/reservations/" + reservationOfA.getId())
                        .header(HttpHeaders.AUTHORIZATION, userBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_canViewOwnReservation() throws Exception {
        Reservation reservationOfA = createReservationFor(userA);

        mockMvc.perform(get("/api/reservations/" + reservationOfA.getId())
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("userA"));
    }

    @Test
    void user_listReservations_onlySeesOwnReservations() throws Exception {
        createReservationFor(userA);
        createReservationFor(userA);
        createReservationFor(userB);

        mockMvc.perform(get("/api/reservations").header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void admin_listReservations_seesAllReservations() throws Exception {
        createReservationFor(userA);
        createReservationFor(userB);

        mockMvc.perform(get("/api/reservations").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void user_cannotDeleteReservation_adminOnly() throws Exception {
        Reservation reservationOfA = createReservationFor(userA);

        mockMvc.perform(delete("/api/reservations/" + reservationOfA.getId())
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_canCancelOwnReservation() throws Exception {
        Reservation reservationOfA = createReservationFor(userA);

        mockMvc.perform(patch("/api/reservations/" + reservationOfA.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void user_cannotCancelOthersReservation() throws Exception {
        Reservation reservationOfA = createReservationFor(userA);

        mockMvc.perform(patch("/api/reservations/" + reservationOfA.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, userBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void filterReservationsByStatusAndPriceRange() throws Exception {
        Reservation r1 = createReservationFor(userA);
        r1.setPrice(new BigDecimal("20.00"));
        r1.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(r1);

        Reservation r2 = createReservationFor(userA);
        r2.setPrice(new BigDecimal("200.00"));
        r2.setStatus(ReservationStatus.PENDING);
        reservationRepository.save(r2);

        mockMvc.perform(get("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .param("status", "CONFIRMED")
                        .param("minPrice", "10")
                        .param("maxPrice", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }

    @Test
    void invalidToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }
}
