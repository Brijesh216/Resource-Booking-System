package com.booking.controller;

import com.booking.dto.auth.LoginRequest;
import com.booking.dto.auth.LoginResponse;
import com.booking.dto.auth.RegisterRequest;
import com.booking.entity.User;
import com.booking.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and registration")
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate and obtain a JWT access token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Self-register a new USER account (ADMIN accounts are provisioned separately)")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name()));
    }

    private record RegisterResponse(Long id, String username, String email, String role) {}
}
