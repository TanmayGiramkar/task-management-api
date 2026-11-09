package com.example.taskapi;

import com.example.taskapi.repository.RefreshTokenRepository;
import com.example.taskapi.repository.TaskRepository;
import com.example.taskapi.repository.UserRepository;
import com.example.taskapi.service.RefreshTokenService;
import com.jayway.jsonpath.JsonPath;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh-token rotation, reuse detection and logout against real PostgreSQL, including the row lock
 * that stops two parallel refreshes with the same token from both succeeding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshTokenIT {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void startWithOneUser() throws Exception {
        // Throwaway Testcontainers database only. Deleting users also deletes their refresh tokens (ON DELETE CASCADE)
        taskRepository.deleteAll();
        userRepository.deleteAll();
        postJson("/auth/register", "{\"email\": \"tanmay@example.com\", \"password\": \"" + PASSWORD + "\"}")
                .andExpect(status().isCreated());
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Logs in and returns the response body (access and refresh token). */
    private String login() throws Exception {
        return postJson("/auth/login", "{\"email\": \"tanmay@example.com\", \"password\": \"" + PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/auth/refresh", "{\"refreshToken\": \"" + refreshToken + "\"}");
    }

    private static String refreshTokenIn(String body) {
        return JsonPath.read(body, "$.refreshToken");
    }

    private static void expectRejected(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Refresh token is invalid, expired or revoked"));
    }

    @Test
    void refreshReturnsAWorkingAccessTokenAndANewRefreshToken() throws Exception {
        String first = refreshTokenIn(login());

        String body = refresh(first).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(refreshTokenIn(body)).isNotEqualTo(first);
        mockMvc.perform(get("/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(body, "$.accessToken")))
                .andExpect(status().isOk());
    }

    @Test
    void theDatabaseNeverHoldsTheRawToken() throws Exception {
        String token = refreshTokenIn(login());

        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .satisfies(stored -> assertThat(stored.getTokenHash()).isNotEqualTo(token).hasSize(64));
    }

    @Test
    void aRefreshTokenWorksOnlyOnce() throws Exception {
        String first = refreshTokenIn(login());
        refresh(first).andExpect(status().isOk());

        expectRejected(refresh(first));
    }

    @Test
    void reusingAnOldTokenAlsoRevokesTheTokenThatReplacedIt() throws Exception {
        String stolen = refreshTokenIn(login());
        // The real user refreshes first and gets the replacement...
        String replacement = refreshTokenIn(refresh(stolen).andReturn().getResponse().getContentAsString());

        // ...then the attacker tries the old token: rejected, and the family is revoked
        expectRejected(refresh(stolen));

        // So the replacement no longer works either, and the user must log in again
        expectRejected(refresh(replacement));
    }

    @Test
    void reuseDetectionOnlyAffectsThatLogin() throws Exception {
        String phone = refreshTokenIn(login());
        String laptop = refreshTokenIn(login());

        refresh(phone).andExpect(status().isOk());
        expectRejected(refresh(phone)); // revokes the phone's family only

        refresh(laptop).andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String token = refreshTokenIn(login());

        postJson("/auth/logout", "{\"refreshToken\": \"" + token + "\"}").andExpect(status().isNoContent());

        expectRejected(refresh(token));
    }

    @Test
    void logoutWithAnUnknownTokenStillReturns204() throws Exception {
        postJson("/auth/logout", "{\"refreshToken\": \"never-issued\"}").andExpect(status().isNoContent());
    }

    @Test
    void twoParallelRefreshesWithTheSameTokenCannotBothSucceed() throws Exception {
        String token = refreshTokenIn(login());
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> attempt = () -> {
            start.await();
            try {
                refreshTokenService.rotate(token);
                return "rotated";
            } catch (RuntimeException rejected) {
                return "rejected";
            }
        };

        List<String> outcomes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> first = pool.submit(attempt);
            Future<String> second = pool.submit(attempt);
            start.countDown();
            outcomes.add(first.get());
            outcomes.add(second.get());
        }

        // The row lock makes the second attempt wait, then see the token already revoked
        assertThat(outcomes).containsExactlyInAnyOrder("rotated", "rejected");
    }
}
