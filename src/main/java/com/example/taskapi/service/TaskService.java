package com.example.taskapi.service;

import com.example.taskapi.dto.PageResponse;
import com.example.taskapi.dto.TaskRequest;
import com.example.taskapi.dto.TaskResponse;
import com.example.taskapi.exception.InvalidQueryParameterException;
import com.example.taskapi.exception.TaskNotFoundException;
import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskStatus;
import com.example.taskapi.repository.TaskRepository;
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

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public TaskResponse createTask(TaskRequest request) {

        Task task = new Task(
                request.getTitle(),
                request.getDescription(),
                request.getStatus()
        );

        Task savedTask = taskRepository.save(task);

        return toResponse(savedTask);
    }

    public PageResponse<TaskResponse> getAllTasks(
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

        Specification<Task> spec = TaskSpecifications.hasStatus(status)
                .and(TaskSpecifications.matchesSearch(search));

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<TaskResponse> result = taskRepository.findAll(spec, pageable)
                .map(this::toResponse);

        return PageResponse.from(result);
    }

    public TaskResponse getTaskById(Long id) {

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(
                        "Task not found with id: " + id
                ));

        return toResponse(task);
    }

    public TaskResponse updateTask(Long id, TaskRequest request) {

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(
                        "Task not found with id: " + id
                ));

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());

        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }

        Task updatedTask = taskRepository.save(task);

        return toResponse(updatedTask);
    }

    public TaskResponse patchTask(Long id, TaskPatchRequest request) {

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(
                        "Task not found with id: " + id
                ));

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

    public void deleteTask(Long id) {

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(
                        "Task not found with id: " + id
                ));

        taskRepository.delete(task);
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