package com.tradeup.backend.trade.controller;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TradeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TradeService tradeService;

    @Test
    void contextLoads() {

    }

    @Test
    void 거래를_요청하면_200이_반환된다() throws Exception {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller")
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        String requestBody = """
                {
                    "productId": %d,
                    "buyerId": %d
                }
                """.formatted(product.getId(), buyer.getId());

        // when & then
        mockMvc.perform(
                        post("/api/trades")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.buyerId").value(buyer.getId()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));

    }

    @Test
    void 거래를_수락하면_ACCEPTED가_반환된다() throws Exception {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller")
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Trade trade = tradeService.requestTrade(
                product.getId(),
                buyer.getId()
        );

        // when & then
        mockMvc.perform(
                        post("/api/trades/{tradeId}/accept", trade.getId())
                                .param("sellerId", seller.getId().toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trade.getId()))
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.buyerId").value(buyer.getId()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        Product savedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(savedProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void 거래를_완료하면_COMPLETED가_반환된다() throws Exception {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller")
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer")
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
                                .param("sellerId", seller.getId().toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trade.getId()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD);
    }

}