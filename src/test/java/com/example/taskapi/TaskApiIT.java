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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests: HTTP request → controller → service → JPA → PostgreSQL (Testcontainers) and back.
 * <p>
 * Each test commits for real, like production. The tests are deliberately not {@code @Transactional}:
 * a test transaction would hold the request's changes uncommitted, so Hibernate would never flush
 * them and {@code @PreUpdate} (which sets {@code updatedAt}) would never run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    /** "Bearer <token>" for this test's user. */
    private String authorization;

    @BeforeEach
    void startWithNoTasksAndOneUser() {
        // Runs against the throwaway Testcontainers database only, never the local task-postgres
        taskRepository.deleteAll();
        userRepository.deleteAll();

        // Created directly and given a token from the app's own token service: the login flow itself
        // is tested in AuthenticationIT, so these tests do not depend on it
        User user = userRepository.save(new User("api-test@example.com", "not-used-by-these-tests"));
        authorization = "Bearer " + jwtTokenService.createAccessToken(user.getId());
    }

    /** Sends the request with this test's access token. */
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private ResultActions sendJson(MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    /** Creates a task through the API and returns its id. */
    private long createTask(String json) throws Exception {
        ResultActions result = sendJson(post("/tasks"), json).andExpect(status().isCreated());
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    @Test
    void createStoresTheTaskWithStatusTodoByDefault() throws Exception {
        ResultActions created = sendJson(post("/tasks"), """
                {"title": "Write report", "description": "First draft"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        long id = ((Number) JsonPath.read(body(created), "$.id")).longValue();

        perform(get("/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Write report"))
                .andExpect(jsonPath("$.description").value("First draft"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void createReturnsTheSameTimestampsThatAreStored() throws Exception {
        String created = body(sendJson(post("/tasks"), """
                {"title": "Write report"}
                """));
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        perform(get("/tasks/{id}", id))
                .andExpect(jsonPath("$.createdAt").value((String) JsonPath.read(created, "$.createdAt")))
                .andExpect(jsonPath("$.updatedAt").value((String) JsonPath.read(created, "$.updatedAt")));
    }

    @Test
    void putReplacesTheTaskClearingAnOmittedDescriptionButKeepingAnOmittedStatus() throws Exception {
        long id = createTask("""
                {"title": "Write report", "description": "First draft", "status": "IN_PROGRESS"}
                """);

        ResultActions replaced = sendJson(put("/tasks/{id}", id), """
                {"title": "Write final report"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Write final report"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        String json = body(replaced);
        String updatedAt = JsonPath.read(json, "$.updatedAt");
        assertThat(LocalDateTime.parse(updatedAt)).isAfter(LocalDateTime.parse(JsonPath.read(json, "$.createdAt")));

        perform(get("/tasks/{id}", id))
                .andExpect(jsonPath("$.title").value("Write final report"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.updatedAt").value(updatedAt));
    }

    @Test
    void patchChangesOnlyTheFieldsSent() throws Exception {
        long id = createTask("""
                {"title": "Write report", "description": "First draft"}
                """);

        sendJson(patch("/tasks/{id}", id), """
                {"status": "COMPLETED"}
                """)
                .andExpect(status().isOk());

        perform(get("/tasks/{id}", id))
                .andExpect(jsonPath("$.title").value("Write report"))
                .andExpect(jsonPath("$.description").value("First draft"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void deleteRemovesTheTask() throws Exception {
        long id = createTask("""
                {"title": "Write report"}
                """);

        perform(delete("/tasks/{id}", id)).andExpect(status().isNoContent());

        perform(get("/tasks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Task not found with id: " + id));
        assertThat(taskRepository.existsById(id)).isFalse();
    }

    @Test
    void everyOperationOnAMissingTaskReturns404() throws Exception {
        long missingId = 999_999L;

        perform(get("/tasks/{id}", missingId)).andExpect(status().isNotFound());
        sendJson(put("/tasks/{id}", missingId), "{\"title\": \"New\"}").andExpect(status().isNotFound());
        sendJson(patch("/tasks/{id}", missingId), "{}").andExpect(status().isNotFound());
        perform(delete("/tasks/{id}", missingId)).andExpect(status().isNotFound());
    }

    @Test
    void listFiltersBySearchAndStatusTogether() throws Exception {
        saveFiveTasks();

        perform(get("/tasks").param("status", "TODO").param("search", "REPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Write project report")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listSortsByTheRequestedField() throws Exception {
        saveFiveTasks();

        perform(get("/tasks").param("sortBy", "title").param("direction", "desc"))
                .andExpect(jsonPath("$.content[*].title", contains(
                        "Write project report", "Review pull request", "Plan sprint",
                        "Fix login bug", "Buy groceries")));
    }

    @Test
    void listOrdersTasksWithTheSameSortValueById() throws Exception {
        saveFiveTasks();

        // Three tasks share status TODO; the id tiebreaker keeps them in insertion order
        perform(get("/tasks").param("sortBy", "status"))
                .andExpect(jsonPath("$.content[?(@.status == 'TODO')].title", contains(
                        "Write project report", "Plan sprint", "Fix login bug")));
    }

    @Test
    void listPaginatesWithCorrectTotals() throws Exception {
        saveFiveTasks();

        perform(get("/tasks").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content[*].title", contains("Buy groceries", "Plan sprint")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));

        perform(get("/tasks").param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.content[*].title", contains("Fix login bug")))
                .andExpect(jsonPath("$.last").value(true));
    }

    /** Saved in this order, so ids increase in this order. */
    private void saveFiveTasks() {
        taskRepository.saveAll(List.of(
                new Task("Write project report", "Summarise the Stage 2 changes", TaskStatus.TODO),
                new Task("Review pull request", "Check the report format", TaskStatus.IN_PROGRESS),
                new Task("Buy groceries", null, TaskStatus.COMPLETED),
                new Task("Plan sprint", "Discuss the roadmap", TaskStatus.TODO),
                new Task("Fix login bug", null, TaskStatus.TODO)));
    }
}
