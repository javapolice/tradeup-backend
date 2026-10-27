package com.tradeup.backend.trade.controller;

import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.service.TradeService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TradePurchasesIntegrationTest {

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
    private EntityManager entityManager;

    // 실제 서비스 로직을 유지하면서 Controller의 호출 여부를 관찰한다.
    @MockitoSpyBean
    private TradeService tradeService;

    @Test
    void A의_JWT로_A의_구매_내역을_조회하면_A의_거래만_반환된다() throws Exception {
        Purchases fixture = createPurchases();

        MvcResult result = mockMvc.perform(get("/api/members/{memberId}/purchases", fixture.buyerAId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.buyerAId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        String json = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Number> tradeIds = JsonPath.read(json, "$[*].id");
        List<Number> buyerIds = JsonPath.read(json, "$[*].buyerId");
        assertThat(tradeIds.stream().map(Number::longValue).toList())
                .containsExactlyInAnyOrder(fixture.firstATradeId(), fixture.secondATradeId())
                .doesNotContain(fixture.bTradeId());
        assertThat(buyerIds.stream().map(Number::longValue).toList())
                .containsOnly(fixture.buyerAId())
                .doesNotContain(fixture.buyerBId());
        verify(tradeService).getPurchases(fixture.buyerAId());
        verifyNoMoreInteractions(tradeService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void A의_JWT로_B의_구매_내역을_조회하면_서비스_호출_전에_거부된다(boolean spoofQuery) throws Exception {
        Purchases fixture = createPurchases();
        assertThat(fixture.buyerAId()).isNotEqualTo(fixture.buyerBId());
        assertThat(memberRepository.existsById(fixture.buyerBId())).isTrue();
        var request = get("/api/members/{memberId}/purchases", fixture.buyerBId())
                .header(HttpHeaders.AUTHORIZATION, bearer(fixture.buyerAId()));
        if (spoofQuery) {
            request.param("memberId", fixture.buyerAId().toString())
                    .param("authenticatedMemberId", fixture.buyerBId().toString());
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value("본인의 구매 내역만 조회할 수 있습니다."))
                .andExpect(jsonPath("$.id").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.buyerId").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.productId").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.purchases").doesNotHaveJsonPath())
                .andReturn();
        assertThat(result.getResolvedException()).isExactlyInstanceOf(BusinessException.class);
        BusinessException exception = (BusinessException) result.getResolvedException();
        assertThat(exception.getErrorCode()).isSameAs(ErrorCode.FORBIDDEN);
        assertThat(exception.getMessage()).isEqualTo("본인의 구매 내역만 조회할 수 있습니다.");

        // B의 구매 내역을 조회하거나 응답 DTO로 변환하기 전에 차단되어야 한다.
        verifyNoInteractions(tradeService);
    }

    @Test
    void JWT_없는_구매_내역_조회는_401이고_서비스를_호출하지_않는다() throws Exception {
        Purchases fixture = createPurchases();

        MvcResult result = mockMvc.perform(get("/api/members/{memberId}/purchases", fixture.buyerAId()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        verifyNoInteractions(tradeService);
    }

    @Test
    void 구매_내역이_없는_본인_조회는_200과_빈_배열을_반환한다() throws Exception {
        createPurchases();
        Member member = saveMember("empty-buyer");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/members/{memberId}/purchases", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(tradeService).getPurchases(member.getId());
        verifyNoMoreInteractions(tradeService);
    }

    private Purchases createPurchases() {
        Member seller = saveMember("seller");
        Member buyerA = saveMember("buyer-a");
        Member buyerB = saveMember("buyer-b");
        Product first = productRepository.save(new Product(seller, "First product", "test", 1000L));
        Product second = productRepository.save(new Product(seller, "Second product", "test", 2000L));
        Trade firstA = tradeService.requestTrade(first.getId(), buyerA.getId());
        Trade secondA = tradeService.requestTrade(second.getId(), buyerA.getId());
        Trade tradeB = tradeService.requestTrade(first.getId(), buyerB.getId());
        entityManager.flush();
        entityManager.clear();
        clearInvocations(tradeService);
        return new Purchases(buyerA.getId(), buyerB.getId(), firstA.getId(), secondA.getId(), tradeB.getId());
    }

    private Member saveMember(String name) {
        return memberRepository.save(new Member("purchases-" + name + "@test.com", "purchases-" + name, PASSWORD_HASH));
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(memberId);
    }

    private record Purchases(Long buyerAId, Long buyerBId, Long firstATradeId, Long secondATradeId, Long bTradeId) {}
}
