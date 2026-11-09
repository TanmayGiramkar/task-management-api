package com.example.taskapi;

import com.example.taskapi.repository.TaskRepository;
import com.example.taskapi.repository.UserRepository;
import com.example.taskapi.support.TestClientIp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The production rate limits (application.properties: login 5, register 3, refresh 10 per minute per IP)
 * through the full filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthRateLimitIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private RequestPostProcessor attacker;

    @BeforeEach
    void setUp() {
        // Throwaway Testcontainers database only
        taskRepository.deleteAll();
        userRepository.deleteAll();
        attacker = TestClientIp.uniqueClientIp();
    }

    private ResultActions postJson(RequestPostProcessor clientIp, String path, String json) throws Exception {
        return mockMvc.perform(post(path).with(clientIp).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(RequestPostProcessor clientIp) throws Exception {
        return postJson(clientIp, "/auth/login", """
                {"email": "victim@example.com", "password": "guess-number-n"}
                """);
    }

    private static void expectTooManyRequests(ResultActions result) throws Exception {
        result.andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value(matchesPattern("Too many requests\\. Try again in \\d+ seconds")))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")));
    }

    @Test
    void sixthLoginAttemptWithinAMinuteGets429EvenIfThePasswordWouldBeRight() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            login(attacker).andExpect(status().isUnauthorized());
        }

        expectTooManyRequests(login(attacker));
    }

    @Test
    void fourthRegistrationWithinAMinuteGets429() throws Exception {
        for (int i = 1; i <= 3; i++) {
            postJson(attacker, "/auth/register",
                    "{\"email\": \"user" + i + "@example.com\", \"password\": \"correct-horse\"}")
                    .andExpect(status().isCreated());
        }

        expectTooManyRequests(postJson(attacker, "/auth/register",
                "{\"email\": \"user4@example.com\", \"password\": \"correct-horse\"}"));
    }

    @Test
    void eleventhRefreshWithinAMinuteGets429() throws Exception {
        for (int i = 1; i <= 10; i++) {
            postJson(attacker, "/auth/refresh", "{\"refreshToken\": \"guess-" + i + "\"}")
                    .andExpect(status().isUnauthorized());
        }

        expectTooManyRequests(postJson(attacker, "/auth/refresh", "{\"refreshToken\": \"guess-11\"}"));
    }

    @Test
    void otherClientsAreNotAffected() throws Exception {
        for (int attempt = 1; attempt <= 6; attempt++) {
            login(attacker);
        }

        login(TestClientIp.uniqueClientIp()).andExpect(status().isUnauthorized());
    }
}
