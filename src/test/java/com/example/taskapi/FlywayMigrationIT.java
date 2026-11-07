package com.example.taskapi;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the Flyway migrations on an empty PostgreSQL database.
 * <p>
 * The local database was baselined at V1, so V1 never ran there. Here every migration runs from scratch,
 * as it will on a new server.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationIT {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void everyMigrationRunsSuccessfullyOnAnEmptyDatabase() {
        MigrationInfoService info = flyway.info();

        assertThat(info.pending()).isEmpty();
        assertThat(info.applied())
                .isNotEmpty()
                .allSatisfy(migration -> assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS));
    }

    @Test
    void columnLengthsMatchTheEntityAndTheValidationRules() {
        // ddl-auto=validate checks that columns exist with compatible types, but not VARCHAR lengths,
        // so this catches drift between the migrations, @Column(length) and @Size
        assertThat(columnLength("title")).isEqualTo(100);
        assertThat(columnLength("description")).isEqualTo(500);
    }

    @Test
    void databaseRejectsAnUnknownStatus() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO tasks (title, status, created_at, updated_at)
                VALUES ('Check constraint test', 'DONE', now(), now())"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Integer columnLength(String column) {
        return jdbcTemplate.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_name = 'tasks' AND column_name = ?""", Integer.class, column);
    }
}
