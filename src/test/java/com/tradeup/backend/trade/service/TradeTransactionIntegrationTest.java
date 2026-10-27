package com.tradeup.backend.trade.service;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.domain.TradeStatus;
import com.tradeup.backend.trade.repository.TradeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// Deliberately has no @Transactional: fixture setup commits, and each observation uses a new transaction.
@SpringBootTest
@AutoConfigureMockMvc
class TradeTransactionIntegrationTest {
    private static final String HASH = new BCryptPasswordEncoder().encode("transaction-test-password");
    @Autowired private TradeService service;
    @Autowired private MemberRepository members;
    @Autowired private ProductRepository products;
    @Autowired private TradeRepository trades;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    private Fixture fixture;

    @AfterEach
    void cleanUpCommittedFixture() {
        if (fixture == null) return;
        tx().executeWithoutResult(status -> {
            trades.deleteAllById(List.of(fixture.tradeId(), fixture.otherTradeId()));
            entityManager.flush();
            products.deleteById(fixture.productId());
            entityManager.flush();
            members.deleteAllById(List.of(fixture.sellerId(), fixture.buyerId(), fixture.otherBuyerId()));
            entityManager.flush();
        });
    }

    @Test
    void 수락과_완료는_Service_자체_트랜잭션으로_커밋된다() {
        setUpFixture(false);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        service.acceptTrade(fixture.tradeId(), fixture.sellerId());
        assertCommittedStates(ProductStatus.RESERVED, TradeStatus.ACCEPTED, TradeStatus.REJECTED);

        service.completeTrade(fixture.tradeId(), fixture.sellerId());
        assertCommittedStates(ProductStatus.SOLD, TradeStatus.COMPLETED, TradeStatus.REJECTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "complete"})
    void 엔티티_검사_실패는_Service_트랜잭션의_선행_거래_변경을_롤백한다(String action) {
        setUpFixture(action.equals("complete"));
        // Deliberately inconsistent imported state exercises failure after trade.accept()/complete().
        tx().executeWithoutResult(status -> {
            Product product = products.findById(fixture.productId()).orElseThrow();
            if (action.equals("accept")) product.reserve();
            else product.sold();
        });
        Snapshot before = snapshot();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        assertThatThrownBy(() -> change(action))
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage(action.equals("accept")
                        ? "판매 중인 상품만 예약할 수 있습니다." : "예약된 상품만 판매 완료할 수 있습니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));

        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "complete"})
    void 양쪽_상태를_SQL로_flush한_뒤_커밋_직전_실패해도_함께_롤백된다(String action) {
        setUpFixture(action.equals("complete"));
        Snapshot before = snapshot();
        AtomicBoolean flushed = new AtomicBoolean();
        String expectedProduct = action.equals("accept") ? "RESERVED" : "SOLD";
        String expectedTrade = action.equals("accept") ? "ACCEPTED" : "COMPLETED";

        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    entityManager.flush();
                    // Native SQL verifies actual uncommitted DB writes, not only persistence-context values.
                    assertThat(dbStatus("products", fixture.productId())).isEqualTo(expectedProduct);
                    assertThat(dbStatus("trades", fixture.tradeId())).isEqualTo(expectedTrade);
                    assertThat(dbStatus("trades", fixture.otherTradeId())).isEqualTo("REJECTED");
                    flushed.set(true);
                    throw new IllegalStateException("test failure after SQL flush");
                }
            });
            change(action); // Service joins this real caller transaction.
        })).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("test failure after SQL flush");

        assertThat(flushed).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"accept, RESERVED", "accept, SOLD", "complete, SELLING", "complete, SOLD"})
    void 인위적_불일치_조합의_상품_전이_실패도_HTTP409와_실제_롤백을_보장한다(
            String action, ProductStatus initialProductStatus
    ) throws Exception {
        setUpFixture(false);
        // Deliberately bypass aggregate coordination to exercise Product guards after Trade mutation.
        // Each individual state is reached only through public entity methods.
        tx().executeWithoutResult(transaction -> {
            Trade trade = trades.findById(fixture.tradeId()).orElseThrow();
            if (action.equals("complete")) trade.accept();
            Product product = products.findById(fixture.productId()).orElseThrow();
            if (initialProductStatus != ProductStatus.SELLING) product.reserve();
            if (initialProductStatus == ProductStatus.SOLD) product.sold();
        });
        Snapshot before = snapshot();
        assertThat(before.productStatus()).isEqualTo(initialProductStatus);
        assertThat(before.rows().stream().filter(row -> row.id().equals(fixture.tradeId())).findFirst().orElseThrow().status())
                .isEqualTo(action.equals("accept") ? TradeStatus.REQUESTED : TradeStatus.ACCEPTED);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        String message = action.equals("accept")
                ? "판매 중인 상품만 예약할 수 있습니다." : "예약된 상품만 판매 완료할 수 있습니다.";

        mockMvc.perform(post("/api/trades/{tradeId}/{action}", fixture.tradeId(), action)
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(fixture.sellerId())))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(message))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isExactlyInstanceOf(BusinessException.class);
                    BusinessException exception = (BusinessException) result.getResolvedException();
                    assertThat(exception.getErrorCode()).isSameAs(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).isEqualTo(message);
                });

        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(snapshot()).isEqualTo(before);
    }

    private String dbStatus(String table, long id) {
        return (String) entityManager.createNativeQuery("SELECT status FROM " + table + " WHERE id = :id")
                .setParameter("id", id).getSingleResult();
    }

    private void change(String action) {
        if (action.equals("accept")) service.acceptTrade(fixture.tradeId(), fixture.sellerId());
        else service.completeTrade(fixture.tradeId(), fixture.sellerId());
    }

    private void setUpFixture(boolean accepted) {
        fixture = tx().execute(status -> {
            String prefix = "trade-tx-" + UUID.randomUUID();
            Member seller = members.save(new Member(prefix + "-s@test.com", prefix + "-s", HASH));
            Member buyer = members.save(new Member(prefix + "-b@test.com", prefix + "-b", HASH));
            Member other = members.save(new Member(prefix + "-o@test.com", prefix + "-o", HASH));
            Product product = products.save(new Product(seller, "transaction product", "description", 1000L));
            Trade trade = service.requestTrade(product.getId(), buyer.getId());
            Trade otherTrade = service.requestTrade(product.getId(), other.getId());
            if (accepted) service.acceptTrade(trade.getId(), seller.getId());
            return new Fixture(seller.getId(), buyer.getId(), other.getId(), product.getId(), trade.getId(), otherTrade.getId());
        });
    }

    private void assertCommittedStates(ProductStatus productStatus, TradeStatus tradeStatus, TradeStatus otherStatus) {
        Snapshot saved = snapshot();
        assertThat(saved.productStatus()).isEqualTo(productStatus);
        assertThat(saved.sellerId()).isEqualTo(fixture.sellerId());
        assertThat(saved.title()).isEqualTo("transaction product");
        assertThat(saved.description()).isEqualTo("description");
        assertThat(saved.price()).isEqualTo(1000L);
        assertThat(saved.rows()).containsExactly(
                new TradeRow(fixture.tradeId(), fixture.productId(), fixture.buyerId(), tradeStatus),
                new TradeRow(fixture.otherTradeId(), fixture.productId(), fixture.otherBuyerId(), otherStatus));
    }

    private Snapshot snapshot() {
        return tx().execute(status -> {
            Product product = products.findById(fixture.productId()).orElseThrow();
            List<TradeRow> rows = trades.findAllByProductId(fixture.productId()).stream()
                    .sorted(Comparator.comparing(Trade::getId))
                    .map(trade -> new TradeRow(trade.getId(), trade.getProduct().getId(), trade.getBuyer().getId(), trade.getStatus()))
                    .toList();
            return new Snapshot(product.getSeller().getId(), product.getTitle(), product.getDescription(),
                    product.getPrice(), product.getStatus(), rows);
        });
    }

    private TransactionTemplate tx() { return new TransactionTemplate(transactionManager); }
    private record Fixture(Long sellerId, Long buyerId, Long otherBuyerId, Long productId, Long tradeId, Long otherTradeId) {}
    private record TradeRow(Long id, Long productId, Long buyerId, TradeStatus status) {}
    private record Snapshot(Long sellerId, String title, String description, Long price, ProductStatus productStatus,
                            List<TradeRow> rows) {}
}
