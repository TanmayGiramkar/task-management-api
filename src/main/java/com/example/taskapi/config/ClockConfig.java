package com.example.taskapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * One Clock for code that compares times (token expiry). Tests pass a fixed Clock instead,
 * so expiry can be checked without waiting.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
