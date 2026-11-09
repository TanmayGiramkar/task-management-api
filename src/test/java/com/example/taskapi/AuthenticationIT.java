package com.example.taskapi;

import com.example.taskapi.repository.TaskRepository;
import com.example.taskapi.repository.UserRepository;
import com.example.taskapi.security.JwtProperties;
import com.example.taskapi.support.TestClientIp;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration, login and access-token checks through the real security filter chain, BCrypt and
 * PostgreSQL (Testcontainers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthenticationIT {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    /** Each test calls from its own IP, so the per-IP rate limits never carry over between tests. */
    private RequestPostProcessor clientIp;

    @BeforeEach
    void startWithNoUsers() {
        // Throwaway Testcontainers database only
        taskRepository.deleteAll();
        userRepository.deleteAll();
        clientIp = TestClientIp.uniqueClientIp();
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/register").with(clientIp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").with(clientIp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }

    private String accessTokenFor(String email) throws Exception {
        register(email, PASSWORD).andExpect(status().isCreated());
        String body = login(email, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions getTasksWith(String authorizationHeader) throws Exception {
        return mockMvc.perform(get("/tasks").header(HttpHeaders.AUTHORIZATION, authorizationHeader));
    }

    private static void expectInvalidToken(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Access token is invalid or expired"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void registerLoginAndUseTheAccessToken() throws Exception {
        register("Tanmay@Example.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("tanmay@example.com"));

        String body = login("tanmay@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();

        getTasksWith("Bearer " + JsonPath.read(body, "$.accessToken"))
                .andExpect(status().isOk());
    }

    @Test
    void storesOnlyABcryptHashOfThePassword() throws Exception {
        register("tanmay@example.com", PASSWORD).andExpect(status().isCreated());

        String hash = userRepository.findByEmail("tanmay@example.com").orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
    }

    @Test
    void loginIgnoresEmailCase() throws Exception {
        register("tanmay@example.com", PASSWORD).andExpect(status().isCreated());

        login("  TANMAY@example.COM ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        register("tanmay@example.com", PASSWORD).andExpect(status().isCreated());

        for (ResultActions result : new ResultActions[]{
                login("tanmay@example.com", "wrong-password"),
                login("nobody@example.com", PASSWORD)}) {
            result.andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }
    }

    @Test
    void registeringAnEmailTwiceReturns409EvenWithDifferentCase() throws Exception {
        register("tanmay@example.com", PASSWORD).andExpect(status().isCreated());

        register("TANMAY@example.com", "another-password")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void passwordOver72BytesIsRejectedWith400() throws Exception {
        register("tanmay@example.com", "é".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password must not exceed 72 bytes"));
    }

    @Test
    void tasksNeedAToken() throws Exception {
        mockMvc.perform(get("/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Authentication is required"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        long userId = JsonPath.<Number>read(register("tanmay@example.com", PASSWORD)
                .andReturn().getResponse().getContentAsString(), "$.id").longValue();
        // Expired 10 minutes ago: well past the decoder's 60-second clock-skew allowance
        Instant issued = Instant.now().minus(Duration.ofMinutes(25));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(Long.toString(userId))
                .issuedAt(issued)
                .expiresAt(issued.plus(Duration.ofMinutes(15)))
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();

        expectInvalidToken(getTasksWith("Bearer " + expired));
    }

    @Test
    void tokenWithAChangedPayloadIsRejected() throws Exception {
        String[] parts = accessTokenFor("tanmay@example.com").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = payload.replaceFirst("\"sub\":\"\\d+\"", "\"sub\":\"999\"");
        assertThat(forgedPayload).isNotEqualTo(payload);

        expectInvalidToken(getTasksWith("Bearer " + parts[0] + "." + base64Url(forgedPayload) + "." + parts[2]));
    }

    @Test
    void unsignedTokenWithAlgNoneIsRejected() throws Exception {
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"iss\":\"" + jwtProperties.issuer() + "\",\"sub\":\"1\",\"exp\":"
                + Instant.now().plus(Duration.ofMinutes(10)).getEpochSecond() + "}");

        expectInvalidToken(getTasksWith("Bearer " + header + "." + payload + "."));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        JwtEncoder attackerEncoder = NimbusJwtEncoder
                .withSecretKey(new SecretKeySpec(otherKey, "HmacSHA256")).algorithm(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .build();
        String forged = attackerEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        expectInvalidToken(getTasksWith("Bearer " + forged));
    }
}
