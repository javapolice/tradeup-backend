package com.tradeup.backend.product.controller;

import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.service.MemberService;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void sellerId_없는_등록_요청은_JWT_회원_A를_DB의_판매자로_저장한다() throws Exception {
        Member memberA = memberService.signUp("product-api-a@test.com", "product-api-a", "password");
        long before = productRepository.count();

        MvcResult result = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtTokenProvider.generateAccessToken(memberA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"JWT owner product","description":"description","price":12345}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value(memberA.getId()))
                .andExpect(jsonPath("$.status").value("SELLING"))
                .andReturn();

        Product saved = reloadProduct(result);
        assertThat(productRepository.count()).isEqualTo(before + 1);
        assertThat(saved.getSeller().getId()).isEqualTo(memberA.getId());
        assertThat(saved.getTitle()).isEqualTo("JWT owner product");
        assertThat(saved.getDescription()).isEqualTo("description");
        assertThat(saved.getPrice()).isEqualTo(12345L);
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    void 본문의_다른_회원_B의_sellerId는_무시되고_JWT_회원_A가_판매자로_저장된다() throws Exception {
        Member memberA = memberService.signUp("product-api-a@test.com", "product-api-a", "password");
        Member memberB = memberService.signUp("product-api-b@test.com", "product-api-b", "password");
        assertThat(memberA.getId()).isNotEqualTo(memberB.getId());
        long before = productRepository.count();

        // 실제 MVC의 Jackson 설정을 사용하여 알 수 없는 sellerId 필드 처리도 검증한다.
        MvcResult result = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtTokenProvider.generateAccessToken(memberA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sellerId":%d,"title":"Spoof attempt","description":"test","price":1000}
                                """.formatted(memberB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value(memberA.getId()))
                .andReturn();

        Product saved = reloadProduct(result);
        assertThat(productRepository.count()).isEqualTo(before + 1);
        assertThat(saved.getSeller().getId()).isEqualTo(memberA.getId()).isNotEqualTo(memberB.getId());
    }

    @Test
    void 본인_JWT로_sellerId_없이_상품을_수정한다() throws Exception {
        Product product = createOwnedProduct();
        Long sellerId = product.getSeller().getId();

        mockMvc.perform(patch("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value(sellerId))
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.price").value(2000));

        Product saved = reloadProduct(product.getId());
        assertThat(saved.getSeller().getId()).isEqualTo(sellerId);
        assertThat(saved.getTitle()).isEqualTo("Updated title");
        assertThat(saved.getDescription()).isEqualTo("Updated description");
        assertThat(saved.getPrice()).isEqualTo(2000L);
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    void 본인_JWT로_sellerId_쿼리_없이_상품을_삭제한다() throws Exception {
        Product product = createOwnedProduct();
        long before = productRepository.count();

        mockMvc.perform(delete("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(product.getSeller().getId())))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        assertThat(productRepository.findById(product.getId())).isEmpty();
        assertThat(productRepository.count()).isEqualTo(before - 1);
    }

    @Test
    void A의_JWT로_B의_상품_수정은_서비스에서_거부되고_DB는_변하지_않는다() {
        Product product = createOwnedProduct();
        Member attacker = memberService.signUp("product-attacker@test.com", "product-attacker", "password");
        entityManager.flush();
        entityManager.clear();
        long before = productRepository.count();

        // 권한 예외 매핑이 없으므로 MockMvc는 HTTP 응답 대신 ServletException을 전파한다.
        assertThatThrownBy(() -> mockMvc.perform(patch("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(attacker.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody())))
                .isInstanceOf(ServletException.class)
                .cause()
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 판매자만 수정할 수 있습니다.");

        assertOriginalProduct(product);
        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    void A의_JWT로_B의_상품_삭제는_서비스에서_거부되고_DB는_변하지_않는다() {
        Product product = createOwnedProduct();
        Member attacker = memberService.signUp("product-attacker@test.com", "product-attacker", "password");
        entityManager.flush();
        entityManager.clear();
        long before = productRepository.count();

        assertThatThrownBy(() -> mockMvc.perform(delete("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(attacker.getId()))))
                .isInstanceOf(ServletException.class)
                .cause()
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 판매자만 삭제할 수 있습니다.");

        assertOriginalProduct(product);
        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    void JWT_없는_상품_수정은_401이고_DB는_변하지_않는다() throws Exception {
        Product product = createOwnedProduct();

        mockMvc.perform(patch("/api/products/{productId}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody()))
                .andExpect(status().isUnauthorized());

        assertOriginalProduct(product);
    }

    @Test
    void JWT_없는_상품_삭제는_401이고_DB는_변하지_않는다() throws Exception {
        Product product = createOwnedProduct();

        mockMvc.perform(delete("/api/products/{productId}", product.getId()))
                .andExpect(status().isUnauthorized());

        assertOriginalProduct(product);
    }

    private Product createOwnedProduct() {
        Member seller = memberService.signUp("product-owner@test.com", "product-owner", "password");
        Product product = productRepository.save(new Product(seller, "Original title", "Original description", 1000L));
        entityManager.flush();
        entityManager.clear();
        return product;
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(memberId);
    }

    private String updateBody() {
        return """
                {"title":"Updated title","description":"Updated description","price":2000}
                """;
    }

    private void assertOriginalProduct(Product original) {
        Product saved = reloadProduct(original.getId());
        assertThat(saved.getSeller().getId()).isEqualTo(original.getSeller().getId());
        assertThat(saved.getTitle()).isEqualTo("Original title");
        assertThat(saved.getDescription()).isEqualTo("Original description");
        assertThat(saved.getPrice()).isEqualTo(1000L);
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    private Product reloadProduct(Long productId) {
        entityManager.flush();
        entityManager.clear();
        return productRepository.findById(productId).orElseThrow();
    }

    private Product reloadProduct(MvcResult result) {
        Number productId = JsonPath.read(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");
        return reloadProduct(productId.longValue());
    }
}
