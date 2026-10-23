package com.tradeup.backend.trade.controller;

import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.repository.TradeRepository;
import com.tradeup.backend.trade.service.TradeService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductTradesIntegrationTest {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode("test-password");

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
    private EntityManager entityManager;

    // 실제 DB 조회를 유지하면서 판매자 검증 후에만 목록 쿼리가 실행되는지 관찰한다.
    @MockitoSpyBean
    private TradeRepository tradeRepository;

    @Test
    void 판매자_A의_JWT로_조회하면_해당_상품의_거래만_반환된다() throws Exception {
        Fixture fixture = fixture();

        MvcResult result = mockMvc.perform(get("/api/products/{productId}/trades", fixture.productId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.sellerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        String json = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Number> tradeIds = JsonPath.read(json, "$[*].id");
        List<Number> productIds = JsonPath.read(json, "$[*].productId");
        assertThat(tradeIds.stream().map(Number::longValue).toList())
                .containsExactlyInAnyOrder(fixture.firstTradeId(), fixture.secondTradeId())
                .doesNotContain(fixture.otherProductTradeId());
        assertThat(productIds.stream().map(Number::longValue).toList()).containsOnly(fixture.productId());
        verify(tradeRepository).findAllByProductId(fixture.productId());
        verifyNoMoreInteractions(tradeRepository);
    }

    @Test
    void 구매자_B의_JWT는_거부되고_거래_목록_쿼리를_호출하지_않는다() {
        Fixture fixture = fixture();

        assertNonSellerRejected(fixture.productId(), fixture.buyerId());
    }

    @Test
    void 무관한_회원_C의_JWT도_거부되고_거래_목록_쿼리를_호출하지_않는다() {
        Fixture fixture = fixture();

        assertNonSellerRejected(fixture.productId(), fixture.unrelatedMemberId());
    }

    @Test
    void JWT_없는_상품별_거래_조회는_401이고_거래_저장소를_호출하지_않는다() throws Exception {
        Fixture fixture = fixture();

        MvcResult result = mockMvc.perform(get("/api/products/{productId}/trades", fixture.productId()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        verifyNoInteractions(tradeRepository);
    }

    @Test
    void 없는_상품은_상품_없음_예외를_전파하고_거래_목록을_조회하지_않는다() {
        Fixture fixture = fixture();
        long missingProductId = Long.MAX_VALUE;
        assertThat(productRepository.existsById(missingProductId)).isFalse();

        assertThatThrownBy(() -> mockMvc.perform(get("/api/products/{productId}/trades", missingProductId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.sellerId()))))
                .isInstanceOf(ServletException.class)
                .cause()
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품을 찾을 수 없습니다.");

        verify(tradeRepository, never()).findAllByProductId(anyLong());
    }

    @Test
    void 거래_요청이_없는_상품은_판매자에게_200과_빈_배열을_반환한다() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(get("/api/products/{productId}/trades", fixture.emptyProductId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.sellerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(tradeRepository).findAllByProductId(fixture.emptyProductId());
        verifyNoMoreInteractions(tradeRepository);
    }

    private void assertNonSellerRejected(Long productId, Long memberId) {
        assertThatThrownBy(() -> mockMvc.perform(get("/api/products/{productId}/trades", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))))
                .isInstanceOf(ServletException.class)
                .cause()
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 판매자만 거래 내역을 조회할 수 있습니다.");

        verify(tradeRepository, never()).findAllByProductId(anyLong());
    }

    private Fixture fixture() {
        Member seller = saveMember("seller-a");
        Member buyer = saveMember("buyer-b");
        Member unrelated = saveMember("unrelated-c");
        Member otherBuyer = saveMember("other-buyer");
        Product product = productRepository.save(new Product(seller, "Target product", "test", 1000L));
        Product otherProduct = productRepository.save(new Product(seller, "Other product", "test", 2000L));
        Product emptyProduct = productRepository.save(new Product(seller, "Empty product", "test", 3000L));
        Trade first = tradeService.requestTrade(product.getId(), buyer.getId());
        Trade second = tradeService.requestTrade(product.getId(), otherBuyer.getId());
        Trade other = tradeService.requestTrade(otherProduct.getId(), buyer.getId());
        entityManager.flush();
        entityManager.clear();
        clearInvocations(tradeRepository);
        return new Fixture(seller.getId(), buyer.getId(), unrelated.getId(), product.getId(),
                emptyProduct.getId(), first.getId(), second.getId(), other.getId());
    }

    private Member saveMember(String name) {
        return memberRepository.save(new Member("product-trades-" + name + "@test.com",
                "product-trades-" + name, PASSWORD_HASH));
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(memberId);
    }

    private record Fixture(Long sellerId, Long buyerId, Long unrelatedMemberId, Long productId,
                           Long emptyProductId, Long firstTradeId, Long secondTradeId, Long otherProductTradeId) {}
}
