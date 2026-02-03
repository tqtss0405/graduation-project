package com.poly.graduation_project.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        boolean adminExists = userRepository
                .findByEmail("admin@gmail.com")
                .isPresent();
        if (!adminExists) {
            User admin = new User();
            admin.setEmail("admin@gmail.com");
            admin.setPassword(passwordEncoder.encode("1234"));
            admin.setFullname("ADMIN");
            admin.setRole(true);
            admin.setActive(true);

            userRepository.save(admin);
            System.out.println(">>> ADMIN account created");
        }
    }

}
