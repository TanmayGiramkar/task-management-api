package com.example.taskapi.controller;

import com.example.taskapi.dto.PageResponse;
import com.example.taskapi.dto.TaskPatchRequest;
import com.example.taskapi.dto.TaskRequest;
import com.example.taskapi.dto.TaskResponse;
import com.example.taskapi.exception.InvalidQueryParameterException;
import com.example.taskapi.exception.TaskNotFoundException;
import com.example.taskapi.model.TaskStatus;
import com.example.taskapi.service.TaskService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link TaskController} and {@code GlobalExceptionHandler}.
 * <p>
 * {@code @WebMvcTest} starts only the MVC layer (controllers, advice, JSON, validation), not JPA or the
 * database, and {@code TaskService} is replaced by a mock. So these tests check the HTTP contract:
 * status codes, JSON shape, validation messages and the error body, not the business logic.
 */
@WebMvcTest(TaskController.class)
class TaskControllerTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 7, 10, 30, 15);
    private static final String TIME_JSON = "2026-11-07T10:30:15";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    private static TaskResponse task(long id, String title, String description, TaskStatus status) {
        return new TaskResponse(id, title, description, status, TIME, TIME);
    }

    private ResultActions sendJson(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /**
     * Every error is an RFC 9457 Problem Details body: title (the status phrase), status, detail,
     * instance (the request path) and a timestamp extension, sent as application/problem+json.
     */
    private static void expectProblem(ResultActions result, int status, String detail) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value(HttpStatus.valueOf(status).getReasonPhrase()))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value(result.andReturn().getRequest().getRequestURI()))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Nested
    class CreateTask {

        @Test
        void returns201WithTheCreatedTask() throws Exception {
            when(taskService.createTask(any(TaskRequest.class)))
                    .thenReturn(task(10L, "Write report", "First draft", TaskStatus.TODO));

            sendJson(post("/tasks"), """
                    {"title": "Write report", "description": "First draft"}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.title").value("Write report"))
                    .andExpect(jsonPath("$.description").value("First draft"))
                    .andExpect(jsonPath("$.status").value("TODO"))
                    .andExpect(jsonPath("$.createdAt").value(TIME_JSON))
                    .andExpect(jsonPath("$.updatedAt").value(TIME_JSON));

            ArgumentCaptor<TaskRequest> request = ArgumentCaptor.forClass(TaskRequest.class);
            verify(taskService).createTask(request.capture());
            assertThat(request.getValue().getTitle()).isEqualTo("Write report");
            assertThat(request.getValue().getDescription()).isEqualTo("First draft");
            assertThat(request.getValue().getStatus()).isNull();
        }

        @Test
        void acceptsTitleOfExactly100Characters() throws Exception {
            when(taskService.createTask(any(TaskRequest.class)))
                    .thenReturn(task(10L, "a".repeat(100), null, TaskStatus.TODO));

            sendJson(post("/tasks"), "{\"title\": \"" + "a".repeat(100) + "\"}")
                    .andExpect(status().isCreated());
        }

        @Test
        void rejectsMissingTitle() throws Exception {
            ResultActions result = sendJson(post("/tasks"), "{}");

            expectProblem(result, 400, "Validation failed");
            result.andExpect(jsonPath("$.errors.title").value("Title is required"));
            verifyNoInteractions(taskService);
        }

        @Test
        void rejectsBlankTitle() throws Exception {
            sendJson(post("/tasks"), """
                    {"title": "   "}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").value("Title is required"));
            verifyNoInteractions(taskService);
        }

        @Test
        void rejectsTitleLongerThan100Characters() throws Exception {
            sendJson(post("/tasks"), "{\"title\": \"" + "a".repeat(101) + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").value("Title must not exceed 100 characters"));
            verifyNoInteractions(taskService);
        }

        @Test
        void reportsEveryInvalidField() throws Exception {
            sendJson(post("/tasks"), "{\"title\": \"\", \"description\": \"" + "d".repeat(501) + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").value("Title is required"))
                    .andExpect(jsonPath("$.errors.description").value("Description must not exceed 500 characters"));
            verifyNoInteractions(taskService);
        }

        @Test
        void rejectsMalformedJson() throws Exception {
            ResultActions result = sendJson(post("/tasks"), "{\"title\": ");

            expectProblem(result, 400, "Request body is malformed or contains invalid values");
            verifyNoInteractions(taskService);
        }

        @Test
        void rejectsUnknownStatusValue() throws Exception {
            ResultActions result = sendJson(post("/tasks"), """
                    {"title": "Write report", "status": "DONE"}
                    """);

            expectProblem(result, 400, "Request body is malformed or contains invalid values");
            verifyNoInteractions(taskService);
        }
    }

    @Nested
    class ListTasks {

        private final PageResponse<TaskResponse> onePage = new PageResponse<>(
                List.of(task(10L, "Write report", null, TaskStatus.TODO)), 0, 10, 1, 1, true, true);

        @Test
        void returnsOnePageAndPassesDefaultsToTheService() throws Exception {
            when(taskService.getAllTasks(null, null, null, "asc", 0, 10)).thenReturn(onePage);

            mockMvc.perform(get("/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(10))
                    .andExpect(jsonPath("$.content[0].title").value("Write report"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(true));
        }

        @Test
        void passesEveryQueryParameterToTheService() throws Exception {
            when(taskService.getAllTasks(TaskStatus.IN_PROGRESS, "report", "title", "desc", 2, 5))
                    .thenReturn(onePage);

            mockMvc.perform(get("/tasks")
                            .param("status", "IN_PROGRESS")
                            .param("search", "report")
                            .param("sortBy", "title")
                            .param("direction", "desc")
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk());

            verify(taskService).getAllTasks(TaskStatus.IN_PROGRESS, "report", "title", "desc", 2, 5);
        }

        @Test
        void rejectsUnknownStatusAndListsAllowedValues() throws Exception {
            ResultActions result = mockMvc.perform(get("/tasks").param("status", "DONE"));

            expectProblem(result, 400,
                    "Invalid value 'DONE' for parameter 'status'. Allowed values: [TODO, IN_PROGRESS, COMPLETED]");
            verifyNoInteractions(taskService);
        }

        @Test
        void rejectsNonNumericPage() throws Exception {
            ResultActions result = mockMvc.perform(get("/tasks").param("page", "abc"));

            expectProblem(result, 400, "Invalid value 'abc' for parameter 'page'");
            verifyNoInteractions(taskService);
        }

        @Test
        void returns400WhenTheServiceRejectsAQueryParameter() throws Exception {
            when(taskService.getAllTasks(null, null, null, "asc", 0, 51))
                    .thenThrow(new InvalidQueryParameterException("Page size must be between 1 and 50"));

            ResultActions result = mockMvc.perform(get("/tasks").param("size", "51"));

            expectProblem(result, 400, "Page size must be between 1 and 50");
        }
    }

    @Nested
    class GetTask {

        @Test
        void returnsTheTask() throws Exception {
            when(taskService.getTaskById(10L)).thenReturn(task(10L, "Write report", null, TaskStatus.TODO));

            mockMvc.perform(get("/tasks/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.title").value("Write report"))
                    .andExpect(jsonPath("$.description").value(nullValue()));
        }

        @Test
        void returns404ForUnknownId() throws Exception {
            when(taskService.getTaskById(42L)).thenThrow(new TaskNotFoundException("Task not found with id: 42"));

            expectProblem(mockMvc.perform(get("/tasks/42")), 404, "Task not found with id: 42");
        }

        @Test
        void returns400ForNonNumericId() throws Exception {
            expectProblem(mockMvc.perform(get("/tasks/abc")), 400, "Invalid value 'abc' for parameter 'id'");
            verifyNoInteractions(taskService);
        }
    }

    @Nested
    class ReplaceTask {

        @Test
        void returnsTheReplacedTask() throws Exception {
            when(taskService.updateTask(eq(10L), any(TaskRequest.class)))
                    .thenReturn(task(10L, "New title", null, TaskStatus.COMPLETED));

            sendJson(put("/tasks/10"), """
                    {"title": "New title", "status": "COMPLETED"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("New title"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));

            ArgumentCaptor<TaskRequest> request = ArgumentCaptor.forClass(TaskRequest.class);
            verify(taskService).updateTask(eq(10L), request.capture());
            assertThat(request.getValue().getTitle()).isEqualTo("New title");
            assertThat(request.getValue().getDescription()).isNull();
            assertThat(request.getValue().getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }

        @Test
        void validatesTheBodyLikeCreate() throws Exception {
            sendJson(put("/tasks/10"), "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").value("Title is required"));
            verifyNoInteractions(taskService);
        }

        @Test
        void returns404ForUnknownId() throws Exception {
            when(taskService.updateTask(eq(42L), any(TaskRequest.class)))
                    .thenThrow(new TaskNotFoundException("Task not found with id: 42"));

            expectProblem(sendJson(put("/tasks/42"), """
                    {"title": "New title"}
                    """), 404, "Task not found with id: 42");
        }
    }

    @Nested
    class PatchTask {

        @Test
        void passesOnlyTheSentFieldsToTheService() throws Exception {
            when(taskService.patchTask(10L, new TaskPatchRequest(null, null, TaskStatus.COMPLETED)))
                    .thenReturn(task(10L, "Write report", null, TaskStatus.COMPLETED));

            sendJson(patch("/tasks/10"), """
                    {"status": "COMPLETED"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));

            verify(taskService).patchTask(10L, new TaskPatchRequest(null, null, TaskStatus.COMPLETED));
        }

        @Test
        void acceptsAnEmptyBody() throws Exception {
            when(taskService.patchTask(10L, new TaskPatchRequest(null, null, null)))
                    .thenReturn(task(10L, "Write report", null, TaskStatus.TODO));

            sendJson(patch("/tasks/10"), "{}").andExpect(status().isOk());
        }

        @Test
        void rejectsBlankTitle() throws Exception {
            sendJson(patch("/tasks/10"), """
                    {"title": "   "}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.title").value("Title must not be blank"));
            verifyNoInteractions(taskService);
        }

        @Test
        void rejectsTitleLongerThan100Characters() throws Exception {
            sendJson(patch("/tasks/10"), "{\"title\": \"" + "a".repeat(101) + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").value("Title must not exceed 100 characters"));
            verifyNoInteractions(taskService);
        }

        @Test
        void returns404ForUnknownId() throws Exception {
            when(taskService.patchTask(eq(42L), any(TaskPatchRequest.class)))
                    .thenThrow(new TaskNotFoundException("Task not found with id: 42"));

            expectProblem(sendJson(patch("/tasks/42"), "{}"), 404, "Task not found with id: 42");
        }
    }

    @Nested
    class DeleteTask {

        @Test
        void returns204WithNoBody() throws Exception {
            mockMvc.perform(delete("/tasks/10"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(taskService).deleteTask(10L);
        }

        @Test
        void returns404ForUnknownId() throws Exception {
            doThrow(new TaskNotFoundException("Task not found with id: 42"))
                    .when(taskService).deleteTask(42L);

            expectProblem(mockMvc.perform(delete("/tasks/42")), 404, "Task not found with id: 42");
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void returns405ForUnsupportedMethod() throws Exception {
            expectProblem(mockMvc.perform(post("/tasks/10")), 405,
                    "Request method 'POST' is not supported for this endpoint");
        }

        @Test
        void returns404ForUnknownPath() throws Exception {
            expectProblem(mockMvc.perform(get("/no-such-path")), 404,
                    "No endpoint found for path: /no-such-path");
        }

        @Test
        void returns500WithoutLeakingExceptionDetails() throws Exception {
            when(taskService.getTaskById(10L))
                    .thenThrow(new IllegalStateException("Connection refused: internal-db-host:5432"));

            ResultActions result = mockMvc.perform(get("/tasks/10"));

            expectProblem(result, 500, "An unexpected error occurred");
            result.andExpect(content().string(not(containsString("internal-db-host"))))
                    .andExpect(content().string(not(containsString("IllegalStateException"))))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
    }
}
