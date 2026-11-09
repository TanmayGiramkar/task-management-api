package com.example.taskapi.service;

import com.example.taskapi.dto.PageResponse;
import com.example.taskapi.dto.TaskRequest;
import com.example.taskapi.dto.TaskResponse;
import com.example.taskapi.exception.InvalidQueryParameterException;
import com.example.taskapi.exception.TaskNotFoundException;
import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskStatus;
import com.example.taskapi.repository.TaskRepository;
import com.example.taskapi.repository.UserRepository;
import com.example.taskapi.specification.TaskSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.example.taskapi.dto.TaskPatchRequest;
import java.util.Set;

@Service
public class TaskService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "title", "status", "createdAt", "updatedAt");

    private static final int MAX_PAGE_SIZE = 50;

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    /**
     * Every method takes the id of the logged-in user (from the access token) and only ever reads or
     * changes that user's tasks.
     */
    public TaskResponse createTask(long ownerId, TaskRequest request) {

        // getReferenceById gives a proxy for the foreign key without querying the users table
        Task task = new Task(
                userRepository.getReferenceById(ownerId),
                request.getTitle(),
                request.getDescription(),
                request.getStatus()
        );

        Task savedTask = taskRepository.save(task);

        return toResponse(savedTask);
    }

    public PageResponse<TaskResponse> getAllTasks(
            long ownerId,
            TaskStatus status,
            String search,
            String sortBy,
            String direction,
            int page,
            int size) {

        if (page < 0) {
            throw new InvalidQueryParameterException("Page index must not be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidQueryParameterException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        String sortField = (sortBy == null || sortBy.isBlank()) ? "id" : sortBy;

        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new InvalidQueryParameterException(
                    "Invalid sortBy field: '" + sortBy + "'. Allowed values: " + ALLOWED_SORT_FIELDS);
        }

        Sort.Direction sortDirection;
        if (direction == null || direction.equalsIgnoreCase("asc")) {
            sortDirection = Sort.Direction.ASC;
        } else if (direction.equalsIgnoreCase("desc")) {
            sortDirection = Sort.Direction.DESC;
        } else {
            throw new InvalidQueryParameterException(
                    "Invalid direction: '" + direction + "'. Allowed values: asc, desc");
        }

        Sort sort = Sort.by(sortDirection, sortField);

        if (!sortField.equals("id")) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }

        Specification<Task> spec = TaskSpecifications.ownedBy(ownerId)
                .and(TaskSpecifications.hasStatus(status))
                .and(TaskSpecifications.matchesSearch(search));

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<TaskResponse> result = taskRepository.findAll(spec, pageable)
                .map(this::toResponse);

        return PageResponse.from(result);
    }

    public TaskResponse getTaskById(long ownerId, Long id) {

        return toResponse(findOwnedTask(ownerId, id));
    }

    public TaskResponse updateTask(long ownerId, Long id, TaskRequest request) {

        Task task = findOwnedTask(ownerId, id);

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());

        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }

        Task updatedTask = taskRepository.save(task);

        return toResponse(updatedTask);
    }

    public TaskResponse patchTask(long ownerId, Long id, TaskPatchRequest request) {

        Task task = findOwnedTask(ownerId, id);

        if (request.title() != null) {
            task.setTitle(request.title());
        }

        if (request.description() != null) {
            task.setDescription(request.description());
        }

        if (request.status() != null) {
            task.setStatus(request.status());
        }

        Task updatedTask = taskRepository.save(task);

        return toResponse(updatedTask);
    }

    public void deleteTask(long ownerId, Long id) {

        taskRepository.delete(findOwnedTask(ownerId, id));
    }

    /**
     * Another user's task gets the same 404 and message as a task that does not exist (404, not 403),
     * so ids of other users' tasks cannot be discovered.
     */
    private Task findOwnedTask(long ownerId, Long id) {
        return taskRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new TaskNotFoundException(
                        "Task not found with id: " + id
                ));
    }

    private TaskResponse toResponse(Task task) {

        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}