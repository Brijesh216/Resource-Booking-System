package com.booking.config;

import com.booking.entity.Resource;
import com.booking.entity.Role;
import com.booking.entity.User;
import com.booking.repository.ResourceRepository;
import com.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUsers();
        seedResources();
    }

    private void seedUsers() {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(User.builder()
                    .username("admin")
                    .email("admin@booking.local")
                    .password(passwordEncoder.encode("Admin@123"))
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build());
            log.info("Seeded ADMIN user -> username: admin / password: Admin@123");
        }

        if (!userRepository.existsByUsername("user")) {
            userRepository.save(User.builder()
                    .username("user")
                    .email("user@booking.local")
                    .password(passwordEncoder.encode("User@123"))
                    .role(Role.USER)
                    .enabled(true)
                    .build());
            log.info("Seeded USER user -> username: user / password: User@123");
        }
    }

    private void seedResources() {
        if (resourceRepository.count() == 0) {
            resourceRepository.save(Resource.builder()
                    .name("Conference Room A")
                    .description("Large conference room with projector and video conferencing")
                    .location("Floor 3, Building 1")
                    .capacity(20)
                    .available(true)
                    .build());

            resourceRepository.save(Resource.builder()
                    .name("Meeting Pod 1")
                    .description("Small meeting pod for quick huddles")
                    .location("Floor 1, Building 1")
                    .capacity(4)
                    .available(true)
                    .build());

            resourceRepository.save(Resource.builder()
                    .name("Event Hall")
                    .description("Large hall for events and workshops")
                    .location("Ground Floor, Building 2")
                    .capacity(150)
                    .available(true)
                    .build());

            log.info("Seeded 3 sample resources");
        }
    }
}
