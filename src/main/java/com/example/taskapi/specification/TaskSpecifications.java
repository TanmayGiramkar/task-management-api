package com.example.taskapi.specification;

import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

public final class TaskSpecifications {

    private TaskSpecifications() {
        // Utility class: not meant to be instantiated
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Task> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }
}