package com.tradeup.backend.trade.controller;

import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.member.domain.Member;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.domain.TradeStatus;
import com.tradeup.backend.trade.repository.TradeRepository;
import com.tradeup.backend.trade.service.TradeService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TradeControllerIntegrationTest {

    private static final String PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("test-password");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void contextLoads() {

    }

    @Test
    void 거래를_요청하면_200이_반환된다() throws Exception {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller", PASSWORD_HASH)
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer", PASSWORD_HASH)
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        String requestBody = """
                {
                    "productId": %d
                }
                """.formatted(product.getId());

        // when & then
        MvcResult result = mockMvc.perform(
                        post("/api/trades")
                                .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(buyer.getId()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.buyerId").value(buyer.getId()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn();

        Number tradeId = JsonPath.read(new String(result.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8), "$.id");
        flushAndClear();
        Trade saved = tradeRepository.findById(tradeId.longValue()).orElseThrow();
        assertThat(saved.getBuyer().getId()).isEqualTo(buyer.getId());
        assertThat(saved.getProduct().getId()).isEqualTo(product.getId());
        assertThat(saved.getStatus()).isEqualTo(TradeStatus.REQUESTED);

    }

    @Test
    void 거래를_수락하면_ACCEPTED가_반환된다() throws Exception {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller", PASSWORD_HASH)
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer", PASSWORD_HASH)
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Trade trade = tradeService.requestTrade(
                product.getId(),
                buyer.getId()
        );

        Member otherBuyer = memberRepository.save(new Member("other-buyer@test.com", "other-buyer", PASSWORD_HASH));
        Trade otherTrade = tradeService.requestTrade(product.getId(), otherBuyer.getId());

        // when & then
        mockMvc.perform(
                        post("/api/trades/{tradeId}/accept", trade.getId())
                                .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(seller.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trade.getId()))
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.buyerId").value(buyer.getId()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        flushAndClear();
        Product savedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(savedProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
        assertThat(tradeRepository.findById(trade.getId()).orElseThrow().getStatus()).isEqualTo(TradeStatus.ACCEPTED);
        assertThat(tradeRepository.findById(otherTrade.getId()).orElseThrow().getStatus()).isEqualTo(TradeStatus.REJECTED);
    }

    @Test
    void 거래를_완료하면_COMPLETED가_반환된다() throws Exception {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller", PASSWORD_HASH)
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer", PASSWORD_HASH)
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Trade trade = tradeService.requestTrade(
                product.getId(),
                buyer.getId()
        );

        tradeService.acceptTrade(
                trade.getId(),
                seller.getId()
        );

        // when & then
        mockMvc.perform(
                        post("/api/trades/{tradeId}/complete", trade.getId())
                                .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(seller.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trade.getId()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        flushAndClear();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.SOLD);
        assertThat(tradeRepository.findById(trade.getId()).orElseThrow().getStatus()).isEqualTo(TradeStatus.COMPLETED);
    }

    @Test
    void JSON의_buyerId로_사칭해도_JWT_회원이_DB의_구매자가_된다() throws Exception {
        Fixture fixture = fixture(false);
        Member impersonated = memberRepository.save(new Member("impersonated@test.com", "impersonated", PASSWORD_HASH));
        // 이미 요청한 구매자와 다른 JWT 회원을 사용하여 중복 요청 정책과 분리한다.
        Member requester = memberRepository.save(new Member("requester@test.com", "requester", PASSWORD_HASH));
        long before = tradeRepository.count();

        MvcResult result = mockMvc.perform(post("/api/trades")
                        .header("Authorization", bearer(requester.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"buyerId":%d}
                                """.formatted(fixture.productId(), impersonated.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyerId").value(requester.getId()))
                .andReturn();

        Number tradeId = JsonPath.read(new String(result.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8), "$.id");
        flushAndClear();
        Trade saved = tradeRepository.findById(tradeId.longValue()).orElseThrow();
        assertThat(saved.getBuyer().getId()).isEqualTo(requester.getId()).isNotEqualTo(impersonated.getId());
        assertThat(saved.getProduct().getId()).isEqualTo(fixture.productId());
        assertThat(saved.getStatus()).isEqualTo(TradeStatus.REQUESTED);
        assertThat(tradeRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void 판매자_JWT로_거절하면_다른_요청과_상품은_유지된다() throws Exception {
        Fixture fixture = fixture(false);

        mockMvc.perform(post("/api/trades/{tradeId}/reject", fixture.tradeId())
                        .header("Authorization", bearer(fixture.sellerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        flushAndClear();
        assertThat(tradeRepository.findById(fixture.tradeId()).orElseThrow().getStatus()).isEqualTo(TradeStatus.REJECTED);
        assertThat(tradeRepository.findById(fixture.otherTradeId()).orElseThrow().getStatus()).isEqualTo(TradeStatus.REQUESTED);
        assertThat(productRepository.findById(fixture.productId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject", "complete"})
    void 비판매자_JWT의_상태_변경은_거부되고_관련_DB는_불변이다(String action) {
        Fixture fixture = fixture(action.equals("complete"));
        Snapshot before = snapshot(fixture);
        String message = switch (action) {
            case "accept" -> "상품 판매자만 거래를 수락할 수 있습니다.";
            case "reject" -> "상품 판매자만 거래를 거절할 수 있습니다.";
            default -> "상품 판매자만 거래를 완료할 수 있습니다.";
        };

        // 제거된 sellerId 쿼리를 조작해도 JWT principal을 바꿀 수 없어야 한다.
        assertThatThrownBy(() -> mockMvc.perform(post("/api/trades/{tradeId}/{action}", fixture.tradeId(), action)
                        .header("Authorization", bearer(fixture.buyerId()))
                        .param("sellerId", fixture.sellerId().toString())))
                .isInstanceOf(ServletException.class)
                .cause()
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);

        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject", "complete"})
    void JWT_없는_상태_변경은_401이고_관련_DB는_불변이다(String action) throws Exception {
        Fixture fixture = fixture(action.equals("complete"));
        Snapshot before = snapshot(fixture);

        mockMvc.perform(post("/api/trades/{tradeId}/{action}", fixture.tradeId(), action))
                .andExpect(status().isUnauthorized());

        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    @Test
    void JWT_없는_거래_요청은_401이고_관련_DB는_불변이다() throws Exception {
        Fixture fixture = fixture(false);
        Snapshot before = snapshot(fixture);

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d}
                                """.formatted(fixture.productId())))
                .andExpect(status().isUnauthorized());

        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    private Fixture fixture(boolean accepted) {
        Member seller = memberRepository.save(new Member("jwt-seller@test.com", "jwt-seller", PASSWORD_HASH));
        Member buyer = memberRepository.save(new Member("jwt-buyer@test.com", "jwt-buyer", PASSWORD_HASH));
        Member otherBuyer = memberRepository.save(new Member("jwt-other@test.com", "jwt-other", PASSWORD_HASH));
        Product product = productRepository.save(new Product(seller, "Original title", "Original description", 1000L));
        Trade trade = tradeService.requestTrade(product.getId(), buyer.getId());
        Trade otherTrade = tradeService.requestTrade(product.getId(), otherBuyer.getId());
        if (accepted) {
            tradeService.acceptTrade(trade.getId(), seller.getId());
        }
        flushAndClear();
        return new Fixture(seller.getId(), buyer.getId(), product.getId(), trade.getId(), otherTrade.getId());
    }

    private Snapshot snapshot(Fixture fixture) {
        flushAndClear();
        Product product = productRepository.findById(fixture.productId()).orElseThrow();
        List<TradeRow> trades = tradeRepository.findAllByProductId(fixture.productId()).stream()
                .sorted(Comparator.comparing(Trade::getId))
                .map(trade -> new TradeRow(trade.getId(), trade.getProduct().getId(), trade.getBuyer().getId(), trade.getStatus()))
                .toList();
        return new Snapshot(product.getSeller().getId(), product.getTitle(), product.getDescription(),
                product.getPrice(), product.getStatus(), trades, tradeRepository.count(), productRepository.count());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(memberId);
    }

    private record Fixture(Long sellerId, Long buyerId, Long productId, Long tradeId, Long otherTradeId) {}

    private record TradeRow(Long id, Long productId, Long buyerId, TradeStatus status) {}

    private record Snapshot(Long sellerId, String title, String description, Long price,
                            ProductStatus productStatus, List<TradeRow> trades, long tradeCount, long productCount) {}
}
