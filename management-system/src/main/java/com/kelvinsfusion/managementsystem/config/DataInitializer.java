package com.kelvinsfusion.managementsystem.config;

import com.kelvinsfusion.managementsystem.model.User;
import com.kelvinsfusion.managementsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
private UserRepository userRepository;

@Override
public void run(String... args) throws Exception {
    // Check if Kelvin exists. If not, create one.
    if (userRepository.findByUsername("kelvin").isEmpty()) {
        User admin = new User();
        admin.setUsername("kelvin");
        admin.setPassword("password123");
        admin.setRole("ADMIN");
        admin.setFullName("Kelvin");
        userRepository.save(admin);
        System.out.println("✅ ADMIN ACCOUNT CREATED: kelvin / password123");
    }
}
}
