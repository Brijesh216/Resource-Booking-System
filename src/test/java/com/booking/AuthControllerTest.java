package com.booking;

import com.booking.dto.auth.LoginRequest;
import com.booking.entity.Role;
import com.booking.entity.User;
import com.booking.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .username("testadmin")
                .email("testadmin@test.com")
                .password(passwordEncoder.encode("Password@123"))
                .role(Role.ADMIN)
                .enabled(true)
                .build());
    }

    @Test
    void login_withValidCredentials_returnsJwtToken() throws Exception {
        LoginRequest request = new LoginRequest("testadmin", "Password@123");

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.username").value("testadmin"));
    }

    @Test
    void login_withInvalidPassword_returnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest("testadmin", "wrong-password");

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withMissingFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void passwordsAreStoredWithBcryptHashing() {
        User admin = userRepository.findByUsername("testadmin").orElseThrow();
        assert admin.getPassword().startsWith("$2");
        assert !admin.getPassword().equals("Password@123");
    }
}
