package com.example.taskapi.repository;

import com.example.taskapi.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /**
     * The task only if it belongs to this user. Another user's task and a missing task both come back
     * empty, so the API answers 404 for both and does not reveal which ids exist.
     */
    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);
}
