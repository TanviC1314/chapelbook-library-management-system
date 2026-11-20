package com.chapelbook;

import static org.assertj.core.api.Assertions.assertThat;

import com.chapelbook.support.PostgresTestContainerConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestContainerConfig.class)
class ChapelBookApplicationIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void applicationStartsAndReportsHealthy() {
        var response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void flywayAppliedTheBaselineMigration() {
        List<String> applied = jdbc.queryForList(
                "SELECT script FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        assertThat(applied).contains("V1__enable_postgres_extensions.sql");
    }

    @Test
    void requiredPostgresExtensionsAreInstalled() {
        List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);

        assertThat(extensions).contains("btree_gist", "pg_trgm");
    }
}
