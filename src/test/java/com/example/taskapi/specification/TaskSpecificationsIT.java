package com.example.taskapi.specification;

import com.example.taskapi.TestcontainersConfiguration;
import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskStatus;
import com.example.taskapi.model.User;
import com.example.taskapi.repository.TaskRepository;
import com.example.taskapi.repository.UserRepository;
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

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User otherUser;

    @BeforeEach
    void saveTasks() {
        owner = userRepository.save(new User("owner@example.com", "not-used"));
        otherUser = userRepository.save(new User("other@example.com", "not-used"));
        taskRepository.saveAll(List.of(
                new Task(owner, "Write project report", "Summarise the Stage 2 changes", TaskStatus.TODO),
                new Task(owner, "Review pull request", "Check the REPORT format", TaskStatus.IN_PROGRESS),
                new Task(owner, "Buy groceries", null, TaskStatus.COMPLETED),
                new Task(owner, "Plan sprint", "Discuss the roadmap", TaskStatus.TODO)));
    }

    private List<String> titlesMatching(Specification<Task> spec) {
        return taskRepository.findAll(spec).stream().map(Task::getTitle).toList();
    }

    @Test
    void ownedByMatchesOnlyThatUsersTasks() {
        taskRepository.save(new Task(otherUser, "Other user's report", null, TaskStatus.TODO));

        assertThat(titlesMatching(TaskSpecifications.ownedBy(owner.getId()))).hasSize(4)
                .doesNotContain("Other user's report");
        assertThat(titlesMatching(TaskSpecifications.ownedBy(otherUser.getId())))
                .containsExactly("Other user's report");
    }

    @Test
    void ownedByNeverMatchesTasksWithoutAnOwner() {
        // Tasks created before Stage 4 have no owner (V5) and must be invisible to every user
        taskRepository.save(new Task(null, "Legacy task", null, TaskStatus.TODO));

        assertThat(titlesMatching(TaskSpecifications.ownedBy(owner.getId()))).doesNotContain("Legacy task");
        assertThat(titlesMatching(TaskSpecifications.ownedBy(otherUser.getId()))).isEmpty();
    }

    @Test
    void ownerFilterCombinesWithSearch() {
        taskRepository.save(new Task(otherUser, "Other user's report", null, TaskStatus.TODO));

        Specification<Task> spec = TaskSpecifications.ownedBy(otherUser.getId())
                .and(TaskSpecifications.matchesSearch("report"));

        assertThat(titlesMatching(spec)).containsExactly("Other user's report");
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
