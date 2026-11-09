package com.example.taskapi;

import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskStatus;
import com.example.taskapi.model.User;
import com.example.taskapi.repository.TaskRepository;
import com.example.taskapi.repository.UserRepository;
import com.example.taskapi.security.JwtTokenService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-user access: user B must not be able to see, change or delete user A's tasks. Every attempt
 * gets the same 404 as a task id that does not exist, so B cannot even tell that the task exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskOwnershipIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String alice;
    private String bob;
    private long alicesTaskId;

    @BeforeEach
    void aliceHasOneTask() throws Exception {
        // Throwaway Testcontainers database only
        taskRepository.deleteAll();
        userRepository.deleteAll();
        alice = bearerFor(userRepository.save(new User("alice@example.com", "not-used")));
        bob = bearerFor(userRepository.save(new User("bob@example.com", "not-used")));

        String body = send(alice, post("/tasks"), """
                {"title": "Alice's private task", "description": "Only Alice may see this"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        alicesTaskId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String bearerFor(User user) {
        return "Bearer " + jwtTokenService.createAccessToken(user.getId());
    }

    private ResultActions send(String authorization, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private ResultActions send(String authorization, MockHttpServletRequestBuilder request, String json) throws Exception {
        return send(authorization, request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void expectNotFound(ResultActions result, long id) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Task not found with id: " + id));
    }

    @Test
    void anotherUserGets404ForEveryOperationOnTheTask() throws Exception {
        expectNotFound(send(bob, get("/tasks/{id}", alicesTaskId)), alicesTaskId);
        expectNotFound(send(bob, put("/tasks/{id}", alicesTaskId), "{\"title\": \"Taken over\"}"), alicesTaskId);
        expectNotFound(send(bob, patch("/tasks/{id}", alicesTaskId), "{\"status\": \"COMPLETED\"}"), alicesTaskId);
        expectNotFound(send(bob, delete("/tasks/{id}", alicesTaskId)), alicesTaskId);
    }

    @Test
    void anotherUsersAttemptsChangeNothing() throws Exception {
        send(bob, put("/tasks/{id}", alicesTaskId), "{\"title\": \"Taken over\"}");
        send(bob, patch("/tasks/{id}", alicesTaskId), "{\"status\": \"COMPLETED\"}");
        send(bob, delete("/tasks/{id}", alicesTaskId));

        send(alice, get("/tasks/{id}", alicesTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Alice's private task"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void theAnswerForAnotherUsersTaskMatchesTheAnswerForAMissingTask() throws Exception {
        long missingId = alicesTaskId + 1000;

        String forOtherUsersTask = send(bob, get("/tasks/{id}", alicesTaskId))
                .andReturn().getResponse().getContentAsString()
                .replace("/tasks/" + alicesTaskId, "/tasks/X").replace("id: " + alicesTaskId, "id: X");
        String forMissingTask = send(bob, get("/tasks/{id}", missingId))
                .andReturn().getResponse().getContentAsString()
                .replace("/tasks/" + missingId, "/tasks/X").replace("id: " + missingId, "id: X");

        // Same body apart from the timestamp
        assertThat(forOtherUsersTask.replaceAll("\"timestamp\":\"[^\"]+\"", ""))
                .isEqualTo(forMissingTask.replaceAll("\"timestamp\":\"[^\"]+\"", ""));
    }

    @Test
    void listsAndSearchesShowOnlyYourOwnTasks() throws Exception {
        send(bob, post("/tasks"), "{\"title\": \"Bob's task\"}").andExpect(status().isCreated());

        send(bob, get("/tasks"))
                .andExpect(jsonPath("$.content[*].title", contains("Bob's task")))
                .andExpect(jsonPath("$.totalElements").value(1));
        send(bob, get("/tasks").param("search", "private"))
                .andExpect(jsonPath("$.totalElements").value(0));
        send(alice, get("/tasks"))
                .andExpect(jsonPath("$.content[*].title", contains("Alice's private task")));
    }

    @Test
    void tasksWithoutAnOwnerAreHiddenFromEveryone() throws Exception {
        // Like the tasks that existed before Stage 4 (owner_id is NULL after V5)
        long legacyId = taskRepository.save(new Task(null, "Legacy task", null, TaskStatus.TODO)).getId();

        expectNotFound(send(alice, get("/tasks/{id}", legacyId)), legacyId);
        send(alice, get("/tasks").param("search", "Legacy"))
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(taskRepository.existsById(legacyId)).isTrue();
    }
}
