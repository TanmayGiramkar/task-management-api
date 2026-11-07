package com.example.taskapi.specification;

import com.example.taskapi.TestcontainersConfiguration;
import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskStatus;
import com.example.taskapi.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link TaskSpecifications} against real PostgreSQL, so the generated SQL (LOWER, LIKE, OR, NULL
 * handling) is what production runs. {@code @DataJpaTest} starts only JPA and wraps each test in a
 * transaction that is rolled back, so every test starts from the same four tasks.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class TaskSpecificationsIT {

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void saveTasks() {
        taskRepository.saveAll(List.of(
                new Task("Write project report", "Summarise the Stage 2 changes", TaskStatus.TODO),
                new Task("Review pull request", "Check the REPORT format", TaskStatus.IN_PROGRESS),
                new Task("Buy groceries", null, TaskStatus.COMPLETED),
                new Task("Plan sprint", "Discuss the roadmap", TaskStatus.TODO)));
    }

    private List<String> titlesMatching(Specification<Task> spec) {
        return taskRepository.findAll(spec).stream().map(Task::getTitle).toList();
    }

    @Test
    void hasStatusWithNullMatchesEveryTask() {
        assertThat(titlesMatching(TaskSpecifications.hasStatus(null))).hasSize(4);
    }

    @Test
    void hasStatusMatchesOnlyThatStatus() {
        assertThat(titlesMatching(TaskSpecifications.hasStatus(TaskStatus.TODO)))
                .containsExactlyInAnyOrder("Write project report", "Plan sprint");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void matchesSearchWithoutTextMatchesEveryTask(String search) {
        assertThat(titlesMatching(TaskSpecifications.matchesSearch(search))).hasSize(4);
    }

    @Test
    void matchesSearchIgnoresCase() {
        assertThat(titlesMatching(TaskSpecifications.matchesSearch("PROJECT")))
                .containsExactly("Write project report");
    }

    @Test
    void matchesSearchFindsTextInTitleOrDescription() {
        // "report" is in the first title and, in upper case, in the second description
        assertThat(titlesMatching(TaskSpecifications.matchesSearch("report")))
                .containsExactlyInAnyOrder("Write project report", "Review pull request");
    }

    @Test
    void matchesSearchTrimsSurroundingSpaces() {
        assertThat(titlesMatching(TaskSpecifications.matchesSearch("  roadmap  ")))
                .containsExactly("Plan sprint");
    }

    @Test
    void matchesSearchStillFindsTasksWithNoDescription() {
        assertThat(titlesMatching(TaskSpecifications.matchesSearch("groceries")))
                .containsExactly("Buy groceries");
    }

    @Test
    void matchesSearchReturnsNothingWhenNoTaskMatches() {
        assertThat(titlesMatching(TaskSpecifications.matchesSearch("holiday"))).isEmpty();
    }

    @Test
    void statusAndSearchCombineWithAnd() {
        Specification<Task> spec = TaskSpecifications.hasStatus(TaskStatus.IN_PROGRESS)
                .and(TaskSpecifications.matchesSearch("report"));

        assertThat(titlesMatching(spec)).containsExactly("Review pull request");
    }
}
