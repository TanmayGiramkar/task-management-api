package com.example.taskapi.service;

import com.example.taskapi.dto.PageResponse;
import com.example.taskapi.dto.TaskPatchRequest;
import com.example.taskapi.dto.TaskRequest;
import com.example.taskapi.dto.TaskResponse;
import com.example.taskapi.exception.InvalidQueryParameterException;
import com.example.taskapi.exception.TaskNotFoundException;
import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskStatus;
import com.example.taskapi.model.User;
import com.example.taskapi.repository.TaskRepository;
import com.example.taskapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TaskService}. The repositories are Mockito mocks, so these tests check the
 * service's own rules without Spring or a database. What the Specifications do in SQL is tested
 * against real PostgreSQL in {@code TaskSpecificationsIT}, and cross-user access end to end in
 * {@code TaskOwnershipIT}.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 7, 10, 30, 15);

    /** The logged-in user every test acts as. */
    private static final long OWNER_ID = 7L;
    private static final User OWNER = userWithId(OWNER_ID);

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        // Constructor injection: no Spring needed, just pass the mocks in
        taskService = new TaskService(taskRepository, userRepository);
    }

    private static User userWithId(long id) {
        User user = new User("owner@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /**
     * A task owned by {@link #OWNER}, as if loaded from the database. The entity has no setters for id
     * and timestamps (the database and JPA callbacks own them), so the test sets them by reflection
     * instead of adding setters to production code.
     */
    private static Task storedTask(long id, String title, String description, TaskStatus status) {
        Task task = new Task(OWNER, title, description, status);
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(task, "updatedAt", CREATED_AT);
        return task;
    }

    private static TaskRequest taskRequest(String title, String description, TaskStatus status) {
        TaskRequest request = new TaskRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setStatus(status);
        return request;
    }

    /** Makes save() return the entity it was given, like JPA does for an entity it already manages. */
    private void saveReturnsItsArgument() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    class CreateTask {

        @Test
        void savesTaskWithRequestValuesAndTheCurrentUserAsOwner() {
            when(userRepository.getReferenceById(OWNER_ID)).thenReturn(OWNER);
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
                Task task = invocation.getArgument(0);
                ReflectionTestUtils.setField(task, "id", 1L);
                return task;
            });

            TaskResponse response = taskService.createTask(OWNER_ID,
                    taskRequest("Write report", "First draft", TaskStatus.IN_PROGRESS));

            ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).save(saved.capture());
            assertThat(saved.getValue().getTitle()).isEqualTo("Write report");
            assertThat(saved.getValue().getDescription()).isEqualTo("First draft");
            assertThat(saved.getValue().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(saved.getValue().getOwner()).isSameAs(OWNER);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getTitle()).isEqualTo("Write report");
            assertThat(response.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        }
    }

    @Nested
    class GetTaskById {

        @Test
        void returnsAllFieldsOfTheTask() {
            when(taskRepository.findByIdAndOwnerId(10L, OWNER_ID))
                    .thenReturn(Optional.of(storedTask(10L, "Write report", "First draft", TaskStatus.TODO)));

            TaskResponse response = taskService.getTaskById(OWNER_ID, 10L);

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getTitle()).isEqualTo("Write report");
            assertThat(response.getDescription()).isEqualTo("First draft");
            assertThat(response.getStatus()).isEqualTo(TaskStatus.TODO);
            assertThat(response.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(response.getUpdatedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        void looksUpTasksOnlyAmongTheCurrentUsersTasks() {
            // Another user's task 10 is not returned by the owner-filtered query, so it is "not found"
            when(taskRepository.findByIdAndOwnerId(10L, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.getTaskById(OWNER_ID, 10L))
                    .isInstanceOf(TaskNotFoundException.class)
                    .hasMessage("Task not found with id: 10");
            // Never a lookup by id alone, which would find any user's task
            verify(taskRepository, never()).findById(any());
        }

        @Test
        void throwsNotFoundForUnknownId() {
            when(taskRepository.findByIdAndOwnerId(42L, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.getTaskById(OWNER_ID, 42L))
                    .isInstanceOf(TaskNotFoundException.class)
                    .hasMessage("Task not found with id: 42");
        }
    }

    @Nested
    class UpdateTask {

        @Test
        void replacesTitleDescriptionAndStatus() {
            when(taskRepository.findByIdAndOwnerId(10L, OWNER_ID))
                    .thenReturn(Optional.of(storedTask(10L, "Old title", "Old description", TaskStatus.TODO)));
            saveReturnsItsArgument();

            TaskResponse response = taskService.updateTask(OWNER_ID, 10L,
                    taskRequest("New title", "New description", TaskStatus.COMPLETED));

            assertThat(response.getTitle()).isEqualTo("New title");
            assertThat(response.getDescription()).isEqualTo("New description");
            assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }

        @Test
        void clearsDescriptionWhenOmitted() {
            when(taskRepository.findByIdAndOwnerId(10L, OWNER_ID))
                    .thenReturn(Optional.of(storedTask(10L, "Old title", "Old description", TaskStatus.TODO)));
            saveReturnsItsArgument();

            TaskResponse response = taskService.updateTask(OWNER_ID, 10L, taskRequest("New title", null, null));

            assertThat(response.getDescription()).isNull();
        }

        @Test
        void keepsCurrentStatusWhenOmitted() {
            when(taskRepository.findByIdAndOwnerId(10L, OWNER_ID))
                    .thenReturn(Optional.of(storedTask(10L, "Old title", null, TaskStatus.IN_PROGRESS)));
            saveReturnsItsArgument();

            TaskResponse response = taskService.updateTask(OWNER_ID, 10L, taskRequest("New title", null, null));

            assertThat(response.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        }

        @Test
        void throwsNotFoundAndSavesNothingForUnknownId() {
            when(taskRepository.findByIdAndOwnerId(42L, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.updateTask(OWNER_ID, 42L, taskRequest("New title", null, null)))
                    .isInstanceOf(TaskNotFoundException.class)
                    .hasMessage("Task not found with id: 42");
            verify(taskRepository, never()).save(any(Task.class));
        }
    }

    @Nested
    class PatchTask {

        @Test
        void changesOnlyTheFieldsSent() {
            when(taskRepository.findByIdAndOwnerId(10L, OWNER_ID))
                    .thenReturn(Optional.of(storedTask(10L, "Title", "Description", TaskStatus.TODO)));
            saveReturnsItsArgument();

            TaskResponse response = taskService.patchTask(OWNER_ID, 10L,
                    new TaskPatchRequest(null, null, TaskStatus.COMPLETED));

            assertThat(response.getTitle()).isEqualTo("Title");
            assertThat(response.getDescription()).isEqualTo("Description");
            assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }

        @Test
        void changesEveryFieldWhenAllAreSent() {
            when(taskRepository.findByIdAndOwnerId(10L, OWNER_ID))
                    .thenReturn(Optional.of(storedTask(10L, "Title", "Description", TaskStatus.TODO)));
            saveReturnsItsArgument();

            TaskResponse response = taskService.patchTask(OWNER_ID, 10L,
                    new TaskPatchRequest("New title", "New description", TaskStatus.IN_PROGRESS));

            assertThat(response.getTitle()).isEqualTo("New title");
            assertThat(response.getDescription()).isEqualTo("New description");
            assertThat(response.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        }

        @Test
        void emptyPatchLeavesTaskUnchanged() {
            when(taskRepository.findByIdAndOwnerId(10L, OWNER_ID))
                    .thenReturn(Optional.of(storedTask(10L, "Title", "Description", TaskStatus.TODO)));
            saveReturnsItsArgument();

            TaskResponse response = taskService.patchTask(OWNER_ID, 10L, new TaskPatchRequest(null, null, null));

            assertThat(response.getTitle()).isEqualTo("Title");
            assertThat(response.getDescription()).isEqualTo("Description");
            assertThat(response.getStatus()).isEqualTo(TaskStatus.TODO);
        }

        @Test
        void throwsNotFoundAndSavesNothingForUnknownId() {
            when(taskRepository.findByIdAndOwnerId(42L, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.patchTask(OWNER_ID, 42L, new TaskPatchRequest("New title", null, null)))
                    .isInstanceOf(TaskNotFoundException.class)
                    .hasMessage("Task not found with id: 42");
            verify(taskRepository, never()).save(any(Task.class));
        }
    }

    @Nested
    class DeleteTask {

        @Test
        void deletesTheTask() {
            Task task = storedTask(10L, "Title", null, TaskStatus.TODO);
            when(taskRepository.findByIdAndOwnerId(10L, OWNER_ID)).thenReturn(Optional.of(task));

            taskService.deleteTask(OWNER_ID, 10L);

            verify(taskRepository).delete(task);
        }

        @Test
        void throwsNotFoundAndDeletesNothingForUnknownId() {
            when(taskRepository.findByIdAndOwnerId(42L, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.deleteTask(OWNER_ID, 42L))
                    .isInstanceOf(TaskNotFoundException.class)
                    .hasMessage("Task not found with id: 42");
            verify(taskRepository, never()).delete(any(Task.class));
        }
    }

    @Nested
    class GetAllTasks {

        private final Task task = storedTask(10L, "Write report", null, TaskStatus.TODO);

        /** Stubs findAll to return one page holding {@link #task}, out of {@code total} tasks. */
        private void findAllReturnsPageOf(long total) {
            when(taskRepository.findAll(ArgumentMatchers.<Specification<Task>>any(), any(Pageable.class)))
                    .thenAnswer(invocation -> new PageImpl<>(List.of(task), invocation.getArgument(1), total));
        }

        /** Runs getAllTasks and returns the Pageable the service passed to the repository. */
        private Pageable pageableUsedFor(String sortBy, String direction, int page, int size) {
            taskService.getAllTasks(OWNER_ID, null, null, sortBy, direction, page, size);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(taskRepository).findAll(ArgumentMatchers.<Specification<Task>>any(), pageable.capture());
            return pageable.getValue();
        }

        @Test
        void defaultsToPageZeroSortedByIdOnly() {
            findAllReturnsPageOf(1);

            Pageable pageable = pageableUsedFor(null, "asc", 0, 10);

            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
        }

        @Test
        void sortsByRequestedFieldThenByIdSoPagesAreStable() {
            findAllReturnsPageOf(1);

            Pageable pageable = pageableUsedFor("title", "desc", 0, 10);

            assertThat(pageable.getSort()).isEqualTo(
                    Sort.by(Sort.Direction.DESC, "title").and(Sort.by(Sort.Direction.ASC, "id")));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void fallsBackToIdWhenSortByIsMissing(String sortBy) {
            findAllReturnsPageOf(1);

            Pageable pageable = pageableUsedFor(sortBy, "asc", 0, 10);

            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"asc", "ASC", "Asc"})
        void sortsAscendingWhenDirectionIsAscOrMissing(String direction) {
            findAllReturnsPageOf(1);

            Pageable pageable = pageableUsedFor("title", direction, 0, 10);

            assertThat(pageable.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.ASC);
        }

        @ParameterizedTest
        @ValueSource(strings = {"desc", "DESC", "Desc"})
        void acceptsDescendingInAnyCase(String direction) {
            findAllReturnsPageOf(1);

            Pageable pageable = pageableUsedFor("title", direction, 0, 10);

            assertThat(pageable.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 50})
        void acceptsPageSizesAtTheLimits(int size) {
            findAllReturnsPageOf(1);

            Pageable pageable = pageableUsedFor(null, "asc", 0, size);

            assertThat(pageable.getPageSize()).isEqualTo(size);
        }

        @Test
        void copiesPageMetadataIntoTheResponse() {
            findAllReturnsPageOf(3);

            PageResponse<TaskResponse> response =
                    taskService.getAllTasks(OWNER_ID, TaskStatus.TODO, "report", "id", "asc", 1, 2);

            assertThat(response.content()).extracting(TaskResponse::getId).containsExactly(10L);
            assertThat(response.page()).isEqualTo(1);
            assertThat(response.size()).isEqualTo(2);
            assertThat(response.totalElements()).isEqualTo(3);
            assertThat(response.totalPages()).isEqualTo(2);
            assertThat(response.first()).isFalse();
            assertThat(response.last()).isTrue();
        }

        @Test
        void rejectsNegativePage() {
            assertThatThrownBy(() -> taskService.getAllTasks(OWNER_ID, null, null, null, "asc", -1, 10))
                    .isInstanceOf(InvalidQueryParameterException.class)
                    .hasMessage("Page index must not be negative");
            verifyNoInteractions(taskRepository);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 0, 51})
        void rejectsPageSizeOutsideOneToFifty(int size) {
            assertThatThrownBy(() -> taskService.getAllTasks(OWNER_ID, null, null, null, "asc", 0, size))
                    .isInstanceOf(InvalidQueryParameterException.class)
                    .hasMessage("Page size must be between 1 and 50");
            verifyNoInteractions(taskRepository);
        }

        @ParameterizedTest
        @ValueSource(strings = {"priority", "description", "title; DROP TABLE tasks"})
        void rejectsSortFieldsOutsideTheWhitelist(String sortBy) {
            assertThatThrownBy(() -> taskService.getAllTasks(OWNER_ID, null, null, sortBy, "asc", 0, 10))
                    .isInstanceOf(InvalidQueryParameterException.class)
                    .hasMessageStartingWith("Invalid sortBy field: '" + sortBy + "'");
            verifyNoInteractions(taskRepository);
        }

        @Test
        void rejectsUnknownDirection() {
            assertThatThrownBy(() -> taskService.getAllTasks(OWNER_ID, null, null, null, "sideways", 0, 10))
                    .isInstanceOf(InvalidQueryParameterException.class)
                    .hasMessage("Invalid direction: 'sideways'. Allowed values: asc, desc");
            verifyNoInteractions(taskRepository);
        }
    }
}
