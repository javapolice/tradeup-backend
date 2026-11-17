package com.tradeup.backend.performance.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

/** Opt-in database isolation for Phase 2 performance tests only. */
@SpringBootTest
@ContextConfiguration(initializers = IsolatedPostgresTest.DatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class IsolatedPostgresTest {

    public static class DatabaseInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
                    .withDatabaseName("tradeup_performance")
                    .withUsername("performance_test")
                    .withPassword(UUID.randomUUID().toString())
                    .withReuse(false);
            try {
                postgres.start();
                if (!postgres.isRunning() || postgres.getMappedPort(5432) == 5432) {
                    throw new IllegalStateException("Performance tests require a running container on a non-5432 host port");
                }

                Map<String, Object> properties = Map.ofEntries(
                        Map.entry("spring.datasource.url", postgres.getJdbcUrl()),
                        Map.entry("spring.datasource.username", postgres.getUsername()),
                        Map.entry("spring.datasource.password", postgres.getPassword()),
                        Map.entry("spring.datasource.driver-class-name", "org.postgresql.Driver"),
                        Map.entry("spring.datasource.hikari.jdbc-url", postgres.getJdbcUrl()),
                        Map.entry("spring.datasource.hikari.username", postgres.getUsername()),
                        Map.entry("spring.datasource.hikari.password", postgres.getPassword()),
                        Map.entry("spring.flyway.url", postgres.getJdbcUrl()),
                        Map.entry("spring.flyway.user", postgres.getUsername()),
                        Map.entry("spring.flyway.password", postgres.getPassword()),
                        Map.entry("spring.flyway.enabled", true),
                        Map.entry("spring.flyway.locations", "classpath:db/migration"),
                        Map.entry("spring.jpa.hibernate.ddl-auto", "none"),
                        Map.entry("spring.jpa.open-in-view", false)
                );
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("isolatedPerformancePostgres", properties));

                // Runs before datasource/Flyway beans can open application connections.
                context.addBeanFactoryPostProcessor(beanFactory -> {
                    ConfigurableEnvironment environment = context.getEnvironment();
                    properties.forEach((key, expected) -> {
                        if (!expected.toString().equals(environment.getProperty(key))) {
                            throw new IllegalStateException("Unsafe performance test configuration: " + key);
                        }
                    });
                    if (!postgres.isRunning()) {
                        throw new IllegalStateException("Performance database container is not running");
                    }
                    System.out.println("Verified isolated performance database before bean initialization: "
                            + postgres.getJdbcUrl());
                });
                ((DefaultSingletonBeanRegistry) context.getBeanFactory())
                        .registerDisposableBean("performancePostgresContainer", postgres::stop);
            } catch (RuntimeException | Error failure) {
                postgres.stop();
                throw failure;
            }
        }
    }
}
