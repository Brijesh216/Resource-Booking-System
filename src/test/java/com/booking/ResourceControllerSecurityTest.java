package com.booking;

import com.booking.dto.resource.ResourceRequest;
import com.booking.entity.Resource;
import com.booking.entity.Role;
import com.booking.entity.User;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;
    private String userToken;
    private Long resourceId;

    @BeforeEach
    void setUp() {
        resourceRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder().username("admin1").email("admin1@test.com")
                .password(passwordEncoder.encode("pw")).role(Role.ADMIN).enabled(true).build());
        userRepository.save(User.builder().username("user1").email("user1@test.com")
                .password(passwordEncoder.encode("pw")).role(Role.USER).enabled(true).build());

        adminToken = "Bearer " + jwtUtil.generateToken("admin1", "ADMIN");
        userToken = "Bearer " + jwtUtil.generateToken("user1", "USER");

        Resource resource = resourceRepository.save(Resource.builder()
                .name("Room 1").description("desc").location("loc").capacity(10).available(true).build());
        resourceId = resource.getId();
    }

    @Test
    void anonymousRequest_isRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void user_canReadResources() throws Exception {
        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk());
    }

    @Test
    void user_cannotCreateResource() throws Exception {
        ResourceRequest request = new ResourceRequest("New Room", "desc", "loc", 5, true);

        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_cannotDeleteResource() throws Exception {
        mockMvc.perform(delete("/api/resources/" + resourceId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canCreateResource() throws Exception {
        ResourceRequest request = new ResourceRequest("New Room", "desc", "loc", 5, true);

        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void admin_canDeleteResource() throws Exception {
        mockMvc.perform(delete("/api/resources/" + resourceId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void createResource_withInvalidData_returnsBadRequest() throws Exception {
        ResourceRequest request = new ResourceRequest("", null, "loc", -5, true);

        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
