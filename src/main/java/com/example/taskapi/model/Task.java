package com.example.taskapi.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Task() {
    }

    public Task(String title, String description, TaskStatus status) {
        this.title = title;
        this.description = description;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = currentTime();
        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = TaskStatus.TODO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = currentTime();
    }

    /**
     * The columns are TIMESTAMP(6), so PostgreSQL keeps microseconds. The JVM clock can be finer
     * (100 ns on Windows, nanoseconds on Linux), and the create/update response is built from this
     * object, not re-read from the database. Truncating here makes the response match what is stored.
     */
    private static LocalDateTime currentTime() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}