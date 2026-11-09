package com.example.taskapi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Email + password checking for {@code POST /auth/login}.
 * <p>
 * Kept apart from {@link SecurityConfig} because it needs the database (via {@link UserDetailsService}),
 * while web-layer tests import SecurityConfig without a database.
 */
@Configuration
public class AuthenticationConfig {

    /** BCrypt: slow on purpose and salted per password, so stolen hashes are expensive to crack. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DaoAuthenticationProvider loads the user and compares the BCrypt hash. For an unknown email it
     * still runs a BCrypt comparison, so response time does not reveal which emails have accounts,
     * and both cases fail with the same BadCredentialsException.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
