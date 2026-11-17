package com.tradeup.backend.performance;

import com.tradeup.backend.performance.support.IsolatedPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresIsolationSmokeTest extends IsolatedPostgresTest {

    @Autowired private DataSource dataSource;
    @Autowired private Environment environment;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void connectsOnlyToIsolatedPostgres18WithFlywayV1ThroughV5() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .isEqualTo(environment.getRequiredProperty("spring.datasource.url"))
                    .isEqualTo(environment.getRequiredProperty("spring.flyway.url"))
                    .doesNotContain(":5432/");
            URI target = URI.create(connection.getMetaData().getURL().substring("jdbc:".length()));
            assertThat(target.getPath()).isEqualTo("/tradeup_performance");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
        }
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo("tradeup_performance");
        assertThat(jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class))
                .containsExactly("1", "2", "3", "4", "5");
        assertThat(jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                """, String.class))
                .contains("members", "products", "wishlists", "trades");
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'members' and column_name = 'password_hash'
                """, Integer.class)).isEqualTo(1);
    }
}
