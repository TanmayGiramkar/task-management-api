package com.example.taskapi.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * HTTP security: which paths are public, how requests are authenticated, and how 401/403 are reported.
 * <p>
 * The API is stateless: no server session and no cookies. Every protected request carries a JWT in
 * {@code Authorization: Bearer <token>}, which Spring Security's OAuth2 Resource Server support validates
 * (signature, expiry, issuer). CSRF protection is off because CSRF abuses cookies the browser sends
 * automatically, and this API does not use cookies.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** HS256 needs a key at least as long as its 256-bit output. */
    static final int MIN_SECRET_BYTES = 32;

    private static final String[] PUBLIC_PATHS = {
            "/auth/**",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {

        var authenticationEntryPoint = new ProblemDetailsAuthenticationEntryPoint(exceptionResolver);
        var accessDeniedHandler = new ProblemDetailsAccessDeniedHandler(exceptionResolver);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        return signingKey(properties.secret());
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
        // Accepts only HS256 tokens signed with our key, so "alg": "none" or other algorithms are rejected
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Default validators check exp and nbf (with 60 s clock skew); we also require our issuer
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /** Decodes and checks the configured secret, failing at startup with a clear message. */
    static SecretKey signingKey(String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException(
                    "app.security.jwt.secret is not set. Add JWT_SECRET (Base64, at least 32 bytes) to .env");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be Base64-encoded", exception);
        }
        if (key.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least " + MIN_SECRET_BYTES + " bytes for HS256, but has " + key.length);
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }
}
