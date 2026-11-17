package com.tradeup.backend.performance;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.performance.support.IsolatedPostgresTest;
import com.tradeup.backend.performance.support.MeasuredSqlInspector;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.framework.Advised;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.tradeup.backend.performance.support.MeasuredSqlInspector",
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
        "spring.jpa.properties.hibernate.cache.use_query_cache=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.properties.hibernate.use_sql_comments=false",
        "logging.level.org.hibernate.SQL=DEBUG"
})
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
@Isolated("Measures SessionFactory-wide statistics without concurrent database work")
class ProductNicknameNPlusOneIntegrationTest extends IsolatedPostgresTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private Environment environment;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private ProductRepository products;

    @ParameterizedTest(name = "Seller nickname: {0} products with distinct sellers")
    @ValueSource(ints = {1, 10, 100})
    void measuresNicknameReadsInsideTheServiceTransaction(int productCount) throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertIsolatedTarget();
        Map<Long, ExpectedProduct> expected = createCommittedFixture(productCount);
        assertThat(expected).hasSize(productCount);
        assertThat(expected.values().stream().map(ExpectedProduct::sellerId).toList()).doesNotHaveDuplicates();

        List<Product> observedProducts = new ArrayList<>();
        List<Boolean> initializedBeforeDto = new ArrayList<>();
        AtomicInteger repositoryCalls = new AtomicInteger();
        // Observe the real Spring Data proxy after its query, before the service maps DTOs.
        MethodInterceptor observer = invocation -> {
            if (!invocation.getMethod().getName().equals("findAllByStatus")) {
                return invocation.proceed();
            }
            repositoryCalls.incrementAndGet();
            assertThat(invocation.getArguments()).containsExactly(ProductStatus.SELLING);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            @SuppressWarnings("unchecked")
            List<Product> result = (List<Product>) invocation.proceed();
            observedProducts.addAll(result);
            result.forEach(product -> initializedBeforeDto.add(Hibernate.isInitialized(product.getSeller())));
            return result;
        };
        Advised repositoryProxy = (Advised) products;

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.isStatisticsEnabled()).isTrue();
        Logger logger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
        ListAppender<ILoggingEvent> sqlLog = new ListAppender<>();
        sqlLog.start();
        logger.addAppender(sqlLog);
        MvcResult response;
        List<String> statements;
        long prepared;
        MeasuredSqlInspector.start();
        statistics.clear();
        System.out.printf("PRODUCT_N_PLUS_ONE_BEGIN N=%d%n", productCount);
        try {
            repositoryProxy.addAdvice(0, observer);
            response = mockMvc.perform(get("/api/products")).andReturn();
            prepared = statistics.getPrepareStatementCount();
            statements = MeasuredSqlInspector.snapshot();
        } finally {
            repositoryProxy.removeAdvice(observer);
            MeasuredSqlInspector.stop();
            logger.detachAppender(sqlLog);
            sqlLog.stop();
        }
        List<String> sql = statements.stream().map(ProductNicknameNPlusOneIntegrationTest::normalize).toList();
        List<String> loggedSql = sqlLog.list.stream().map(ILoggingEvent::getFormattedMessage)
                .map(ProductNicknameNPlusOneIntegrationTest::normalize).toList();
        long selects = sql.stream().filter(statement -> statement.startsWith("select ")).count();
        long productSelects = sql.stream().filter(statement -> statement.contains(" from products ")).count();
        long memberSelects = sql.stream().filter(statement -> statement.contains(" from members ")).count();
        long initializedAfterDto = observedProducts.stream()
                .filter(product -> Hibernate.isInitialized(product.getSeller())).count();
        System.out.printf("PRODUCT_N_PLUS_ONE N=%d SELECT=%d PREPARED=%d PRODUCT_SELECT=%d MEMBER_SELECT=%d "
                        + "SQL_LOG=%d SELLER_INITIALIZED_BEFORE=%d AFTER=%d%n",
                productCount, selects, prepared, productSelects, memberSelects, loggedSql.size(),
                initializedBeforeDto.stream().filter(Boolean::booleanValue).count(), initializedAfterDto);
        sql.stream().distinct().forEach(statement -> System.out.println("PRODUCT_N_PLUS_ONE_SQL " + statement));

        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        assertThat(response.getRequest().isAsyncStarted()).isFalse();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        List<Map<String, Object>> rows = JsonPath.read(response.getResponse().getContentAsString(), "$");
        assertThat(rows).hasSize(productCount);
        List<Long> returnedIds = new ArrayList<>();
        rows.forEach(row -> {
            assertThat(row).containsOnlyKeys("id", "sellerId", "title", "description", "price", "status", "sellerNickname");
            long id = ((Number) row.get("id")).longValue();
            returnedIds.add(id);
            assertThat(expected).containsKey(id);
            ExpectedProduct fixture = expected.get(id);
            assertThat(((Number) row.get("sellerId")).longValue()).isEqualTo(fixture.sellerId());
            assertThat(row.get("sellerNickname")).isEqualTo(fixture.nickname());
            assertThat(row.get("title")).isEqualTo(fixture.title());
            assertThat(row.get("description")).isEqualTo("Nickname query fixture");
            assertThat(((Number) row.get("price")).longValue()).isEqualTo(fixture.price());
            assertThat(row.get("status")).isEqualTo("SELLING");
        });
        assertThat(returnedIds).containsExactlyInAnyOrderElementsOf(expected.keySet());
        assertThat(repositoryCalls.get()).isEqualTo(1);
        assertThat(sql).isEqualTo(loggedSql).hasSize(productCount + 1);
        assertThat(selects).isEqualTo(productCount + 1);
        assertThat(prepared).isEqualTo(statements.size());
        assertThat(productSelects).isEqualTo(1);
        assertThat(memberSelects).isEqualTo(productCount);
        assertThat(sql).allSatisfy(statement -> assertThat(statement).startsWith("select ").doesNotContain(" join "));
        assertThat(sql.getFirst()).contains(" from products ", ".status=?");
        assertThat(sql.subList(1, sql.size())).allSatisfy(statement ->
                assertThat(statement).contains(" from members ", ".id=?").doesNotContain(" in ("));
        assertThat(statistics.getEntityLoadCount()).isEqualTo(2L * productCount);
        assertThat(statistics.getEntityFetchCount()).isEqualTo(productCount);
        assertThat(statistics.getEntityInsertCount()).isZero();
        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityDeleteCount()).isZero();
        assertThat(statistics.getFlushCount()).isZero();
        assertThat(initializedBeforeDto).hasSize(productCount).containsOnly(false);
        assertThat(observedProducts).hasSize(productCount);
        assertThat(initializedAfterDto).isEqualTo(productCount);
    }

    @Test
    void createDetailUpdateAndDeleteKeepTheExistingResponseContract() throws Exception {
        assertIsolatedTarget();
        createCommittedFixture(0);
        Long sellerId = new TransactionTemplate(transactionManager).execute(status -> {
            Member seller = new Member("contract@test.com", "contract-seller", "fixture-only-hash");
            entityManager.persist(seller);
            entityManager.flush();
            entityManager.clear();
            return seller.getId();
        });
        String bearer = "Bearer " + tokens.generateAccessToken(sellerId);
        MvcResult created = mockMvc.perform(post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Contract product","description":"Original","price":1000}
                        """)).andReturn();
        long id = ((Number) assertOriginalResponse(created, sellerId, "Original", 1000).get("id")).longValue();
        MvcResult detail = mockMvc.perform(get("/api/products/{id}", id)).andReturn();
        assertThat(((Number) assertOriginalResponse(detail, sellerId, "Original", 1000).get("id")).longValue()).isEqualTo(id);
        MvcResult updated = mockMvc.perform(patch("/api/products/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"description":"Updated","price":2000}
                        """)).andReturn();
        assertThat(((Number) assertOriginalResponse(updated, sellerId, "Updated", 2000).get("id")).longValue()).isEqualTo(id);
        MvcResult deleted = mockMvc.perform(delete("/api/products/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer)).andReturn();
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);
        assertThat(deleted.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private Map<String, Object> assertOriginalResponse(MvcResult response, long sellerId, String description, long price) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> body = JsonPath.read(response.getResponse().getContentAsString(), "$");
        assertThat(body).containsOnlyKeys("id", "sellerId", "title", "description", "price", "status");
        assertThat(((Number) body.get("sellerId")).longValue()).isEqualTo(sellerId);
        assertThat(body.get("title")).isEqualTo("Contract product");
        assertThat(body.get("description")).isEqualTo(description);
        assertThat(((Number) body.get("price")).longValue()).isEqualTo(price);
        assertThat(body.get("status")).isEqualTo("SELLING");
        return body;
    }

    private void assertIsolatedTarget() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .isEqualTo(environment.getRequiredProperty("spring.datasource.url"))
                    .isEqualTo(environment.getRequiredProperty("spring.flyway.url"))
                    .doesNotContain(":5432/").contains("/tradeup_performance?");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
            System.out.println("PRODUCT_N_PLUS_ONE_TARGET " + connection.getMetaData().getURL());
        }
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    }

    private Map<Long, ExpectedProduct> createCommittedFixture(int count) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            entityManager.createQuery("delete from Product").executeUpdate();
            entityManager.createQuery("delete from Member").executeUpdate();
            Map<Long, ExpectedProduct> expected = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String nickname = "seller-" + i;
                Member seller = new Member("nickname-" + i + "@test.com", nickname, "fixture-only-hash");
                entityManager.persist(seller);
                Product product = new Product(seller, "Product " + i, "Nickname query fixture", 1000L + i);
                entityManager.persist(product);
                expected.put(product.getId(), new ExpectedProduct(seller.getId(), nickname, "Product " + i, 1000L + i));
            }
            entityManager.flush();
            entityManager.clear();
            return expected;
        });
    }

    private static String normalize(String sql) {
        return sql.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record ExpectedProduct(Long sellerId, String nickname, String title, long price) {}
}
