package com.example.taskapi.security;

import com.example.taskapi.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a user by email for Spring Security's login check. The email must already be normalised
 * (trimmed, lower-case); {@code AuthService} does that.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("No account with this email"));
    }
}
