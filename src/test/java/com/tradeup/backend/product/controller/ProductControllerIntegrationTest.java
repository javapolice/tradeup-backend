package com.tradeup.backend.product.controller;

import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.service.MemberService;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.domain.TradeStatus;
import com.tradeup.backend.trade.repository.TradeRepository;
import com.tradeup.backend.wishlist.domain.Wishlist;
import com.tradeup.backend.wishlist.repository.WishlistRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerIntegrationTest {

    @ParameterizedTest
    @MethodSource("partialUpdates")
    void PATCH는_요청한_필드만_응답과_DB에_반영한다(
            String body, String title, String description, long price) throws Exception {
        Product product = createOwnedProduct();
        long count = productRepository.count();

        mockMvc.perform(patch("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(product.getSeller().getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.sellerId").value(product.getSeller().getId()))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.description").value(description == null
                        ? org.hamcrest.Matchers.nullValue() : org.hamcrest.Matchers.equalTo(description)))
                .andExpect(jsonPath("$.price").value(price))
                .andExpect(jsonPath("$.status").value("SELLING"));

        Product saved = reloadProduct(product.getId()); // Flush SQL and discard the first-level cache.
        assertThat(saved.getTitle()).isEqualTo(title);
        assertThat(saved.getDescription()).isEqualTo(description);
        assertThat(saved.getPrice()).isEqualTo(price);
        assertThat(saved.getSeller().getId()).isEqualTo(product.getSeller().getId());
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.SELLING);
        assertThat(productRepository.count()).isEqualTo(count);
    }

    static Stream<Arguments> partialUpdates() {
        return Stream.of(
                Arguments.of("{\"title\":\"Changed\"}", "Changed", "Original description", 1000L),
                Arguments.of("{\"price\":2000}", "Original title", "Original description", 2000L),
                Arguments.of("{\"description\":\"Changed\"}", "Original title", "Changed", 1000L),
                Arguments.of("{\"description\":null}", "Original title", null, 1000L),
                Arguments.of("{\"description\":\"\"}", "Original title", "", 1000L),
                Arguments.of("{\"price\":0}", "Original title", "Original description", 0L),
                Arguments.of("{\"title\":\"Changed\",\"price\":2000}", "Changed", "Original description", 2000L),
                Arguments.of("{\"title\":\"Changed\",\"description\":null,\"price\":0}", "Changed", null, 0L),
                Arguments.of("{\"price\":9223372036854775807}", "Original title", "Original description", Long.MAX_VALUE),
                Arguments.of("{\"title\":\"" + "가".repeat(255) + "\",\"description\":\"" + "나".repeat(255) + "\"}",
                        "가".repeat(255), "나".repeat(255), 1000L));
    }

    @ParameterizedTest
    @MethodSource("com.tradeup.backend.product.support.ProductPatchCases#invalidValues")
    void 잘못된_PATCH는_공통_400이며_DB를_보존한다(String body) throws Exception {
        Product product = createOwnedProduct();
        long count = productRepository.count();
        ResultActions result = mockMvc.perform(patch("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(product.getSeller().getId()))
                .contentType(MediaType.APPLICATION_JSON).content(body));

        assertProblem(result, 400, "INVALID_INPUT", "상품 수정 요청 값이 올바르지 않습니다.");
        assertSafePatchProblem(result, product.getId());
        assertOriginalProduct(product);
        assertThat(productRepository.count()).isEqualTo(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "{", "{\"title\":\"PRIVATE_JSON_DIAGNOSTIC\",", "{\"price\":}"})
    void 본문_누락과_JSON_구문_오류는_일반화된_400이며_DB를_보존한다(String body) throws Exception {
        Product product = createOwnedProduct();
        long count = productRepository.count();
        var builder = patch("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(product.getSeller().getId()))
                .contentType(MediaType.APPLICATION_JSON);
        if (!body.isEmpty()) builder.content(body); // Empty case sends no body at all.
        ResultActions result = mockMvc.perform(builder)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.detail").value("요청 본문이 없거나 JSON 형식 또는 자료형이 올바르지 않습니다."))
                .andExpect(response -> assertThat(response.getResolvedException())
                        .isExactlyInstanceOf(org.springframework.http.converter.HttpMessageNotReadableException.class));
        assertSafePatchProblem(result, product.getId());
        assertOriginalProduct(product);
        assertThat(productRepository.count()).isEqualTo(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"title\":\"Changed\"}", "{invalid"})
    void 잘못된_JWT의_PATCH는_본문_검증_전에_401이고_DB를_보존한다(String body) throws Exception {
        Product product = createOwnedProduct();
        mockMvc.perform(patch("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(response -> {
                    assertThat(response.getHandler()).isNull();
                    assertThat(response.getResolvedException()).isNull();
                    assertThat(response.getResponse().getErrorMessage()).isEqualTo("Invalid or expired JWT");
                });
        assertOriginalProduct(product);
    }

    private void assertSafePatchProblem(ResultActions result, Long productId) throws Exception {
        MvcResult response = result.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.instance").value("/api/products/" + productId))
                .andReturn();
        String body = response.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> payload = JsonPath.read(body, "$");
        assertThat(payload).containsOnlyKeys("title", "status", "detail", "instance", "code");
        assertThat(body).doesNotContain("PRIVATE_JSON_DIAGNOSTIC", "tools.jackson", "java.lang",
                "com.tradeup.backend", "Exception", "Cannot deserialize");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private TradeRepository tradeRepository;

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
    void A의_JWT로_B의_상품_수정은_서비스에서_거부되고_DB는_변하지_않는다() throws Exception {
        Product product = createOwnedProduct();
        RelatedData related = createRelatedData(product, TradeStatus.REQUESTED);
        Member attacker = memberService.signUp("product-attacker@test.com", "product-attacker", "password");
        entityManager.flush();
        entityManager.clear();
        long before = productRepository.count();

        assertThat(attacker.getId()).isNotEqualTo(product.getSeller().getId());
        assertProblem(mockMvc.perform(patch("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(attacker.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody())),
                403, "FORBIDDEN", "상품 판매자만 수정할 수 있습니다.");

        assertOriginalProduct(product);
        assertThat(productRepository.count()).isEqualTo(before);
        assertRelatedDataUnchanged(product, related);
    }

    @Test
    void A의_JWT로_B의_상품_삭제는_서비스에서_거부되고_DB는_변하지_않는다() throws Exception {
        Product product = createOwnedProduct();
        RelatedData related = createRelatedData(product, TradeStatus.REQUESTED);
        Member attacker = memberService.signUp("product-attacker@test.com", "product-attacker", "password");
        entityManager.flush();
        entityManager.clear();
        long before = productRepository.count();

        assertThat(attacker.getId()).isNotEqualTo(product.getSeller().getId());
        assertProblem(mockMvc.perform(delete("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(attacker.getId()))),
                403, "FORBIDDEN", "상품 판매자만 삭제할 수 있습니다.");

        assertOriginalProduct(product);
        assertThat(productRepository.count()).isEqualTo(before);
        assertRelatedDataUnchanged(product, related);
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
        RelatedData related = createRelatedData(product, TradeStatus.REQUESTED);

        mockMvc.perform(delete("/api/products/{productId}", product.getId()))
                .andExpect(status().isUnauthorized());

        assertOriginalProduct(product);
        assertRelatedDataUnchanged(product, related);
    }

    @Test
    void DB에_없는_JWT_판매자로_등록하면_404이고_상품이_생성되지_않는다() throws Exception {
        long missingSellerId = Long.MAX_VALUE;
        assertThat(entityManager.find(Member.class, missingSellerId)).isNull();
        long before = productRepository.count();

        assertProblem(mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(missingSellerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody())),
                404, "NOT_FOUND", "판매자를 찾을 수 없습니다.");

        entityManager.flush();
        entityManager.clear();
        assertThat(productRepository.count()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"get", "update", "delete"})
    void 없는_상품의_조회_수정_삭제는_404이며_기존_데이터를_유지한다(String action) throws Exception {
        Product existing = createOwnedProduct();
        RelatedData related = createRelatedData(existing, TradeStatus.REQUESTED);
        long missingProductId = Long.MAX_VALUE;
        assertThat(productRepository.existsById(missingProductId)).isFalse();
        long before = productRepository.count();
        String jwt = bearer(existing.getSeller().getId());

        ResultActions result = switch (action) {
            case "get" -> mockMvc.perform(get("/api/products/{productId}", missingProductId));
            case "update" -> mockMvc.perform(patch("/api/products/{productId}", missingProductId)
                    .header(HttpHeaders.AUTHORIZATION, jwt).contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody()));
            case "delete" -> mockMvc.perform(delete("/api/products/{productId}", missingProductId)
                    .header(HttpHeaders.AUTHORIZATION, jwt));
            default -> throw new AssertionError(action);
        };
        assertProblem(result, 404, "NOT_FOUND", "상품을 찾을 수 없습니다.");

        assertOriginalProduct(existing);
        assertThat(productRepository.count()).isEqualTo(before);
        assertRelatedDataUnchanged(existing, related);
    }

    @ParameterizedTest
    @EnumSource(value = TradeStatus.class, names = {"REQUESTED", "REJECTED"})
    void SELLING_상품도_거래_이력이_있으면_409이고_상품_찜_거래가_보존된다(TradeStatus tradeStatus) throws Exception {
        Product product = createOwnedProduct();
        RelatedData related = createRelatedData(product, tradeStatus);
        assertThat(reloadProduct(product.getId()).getStatus()).isEqualTo(ProductStatus.SELLING);
        long before = productRepository.count();

        assertProblem(mockMvc.perform(delete("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(product.getSeller().getId()))),
                409, "CONFLICT", "거래 이력이 있는 상품은 삭제할 수 없습니다.");

        assertOriginalProduct(product);
        assertThat(productRepository.count()).isEqualTo(before);
        assertRelatedDataUnchanged(product, related);
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"RESERVED", "SOLD"})
    void 판매중이_아닌_상품_삭제는_409이고_관련_데이터를_보존한다(ProductStatus productStatus) throws Exception {
        Product product = createOwnedProduct();
        RelatedData related = createRelatedData(product,
                productStatus == ProductStatus.SOLD ? TradeStatus.COMPLETED : TradeStatus.ACCEPTED);
        Product managed = productRepository.findById(product.getId()).orElseThrow();
        managed.reserve();
        if (productStatus == ProductStatus.SOLD) {
            managed.sold();
        }
        entityManager.flush();
        entityManager.clear();
        long before = productRepository.count();

        // 거래 이력 충돌보다 먼저 발생하는 엔티티의 삭제 상태 오류를 검증한다.
        assertProblem(mockMvc.perform(delete("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(product.getSeller().getId()))),
                409, "CONFLICT", "판매 중인 상품만 삭제할 수 있습니다.");

        Product saved = reloadProduct(product.getId());
        assertProductFieldsUnchanged(saved, product);
        assertThat(saved.getStatus()).isEqualTo(productStatus);
        assertThat(productRepository.count()).isEqualTo(before);
        assertRelatedDataUnchanged(product, related);
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"RESERVED", "SOLD"})
    void 판매중이_아닌_상품_수정은_409이고_모든_필드를_보존한다(ProductStatus productStatus) throws Exception {
        Product product = createOwnedProduct();
        RelatedData related = createRelatedData(product,
                productStatus == ProductStatus.SOLD ? TradeStatus.COMPLETED : TradeStatus.ACCEPTED);
        Product managed = productRepository.findById(product.getId()).orElseThrow();
        managed.reserve();
        if (productStatus == ProductStatus.SOLD) managed.sold();
        entityManager.flush();
        entityManager.clear();
        long before = productRepository.count();

        assertProblem(mockMvc.perform(patch("/api/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(product.getSeller().getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody())),
                409, "CONFLICT", "판매 중인 상품만 수정할 수 있습니다.");

        Product saved = reloadProduct(product.getId());
        assertProductFieldsUnchanged(saved, product);
        assertThat(saved.getStatus()).isEqualTo(productStatus);
        assertThat(productRepository.count()).isEqualTo(before);
        assertRelatedDataUnchanged(product, related);
    }

    @Test
    void 상품_상세와_판매중_목록은_JWT_없이_정상_조회된다() throws Exception {
        Product product = createOwnedProduct();

        mockMvc.perform(get("/api/products/{productId}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.sellerId").value(product.getSeller().getId()))
                .andExpect(jsonPath("$.title").value("Original title"))
                .andExpect(jsonPath("$.description").value("Original description"))
                .andExpect(jsonPath("$.price").value(1000))
                .andExpect(jsonPath("$.status").value("SELLING"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(product.getId())).isNotEmpty());
        assertOriginalProduct(product);
    }

    private void assertProblem(ResultActions result, int httpStatus, String code, String detail) throws Exception {
        result.andExpect(status().is(httpStatus))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.status").value(httpStatus))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(response -> {
                    assertThat(response.getResolvedException()).isExactlyInstanceOf(BusinessException.class);
                    BusinessException exception = (BusinessException) response.getResolvedException();
                    assertThat(exception.getErrorCode().name()).isEqualTo(code);
                    assertThat(exception.getMessage()).isEqualTo(detail);
                });
    }

    private RelatedData createRelatedData(Product product, TradeStatus status) {
        Member buyer = memberService.signUp("product-related@test.com", "product-related", "password");
        Product managed = productRepository.findById(product.getId()).orElseThrow();
        Wishlist wishlist = wishlistRepository.save(new Wishlist(buyer, managed));
        Trade trade = new Trade(managed, buyer);
        switch (status) {
            case REQUESTED -> { }
            case REJECTED -> trade.reject();
            case ACCEPTED -> trade.accept();
            case COMPLETED -> {
                trade.accept();
                trade.complete();
            }
        }
        tradeRepository.save(trade);
        entityManager.flush();
        entityManager.clear();
        return new RelatedData(wishlist.getId(), trade.getId(), buyer.getId(), status,
                wishlistRepository.count(), tradeRepository.count());
    }

    private void assertRelatedDataUnchanged(Product product, RelatedData related) {
        entityManager.flush();
        entityManager.clear();
        Wishlist wishlist = wishlistRepository.findById(related.wishlistId()).orElseThrow();
        assertThat(wishlist.getMember().getId()).isEqualTo(related.buyerId());
        assertThat(wishlist.getProduct().getId()).isEqualTo(product.getId());
        Trade trade = tradeRepository.findById(related.tradeId()).orElseThrow();
        assertThat(trade.getBuyer().getId()).isEqualTo(related.buyerId());
        assertThat(trade.getProduct().getId()).isEqualTo(product.getId());
        assertThat(trade.getStatus()).isEqualTo(related.status());
        assertThat(wishlistRepository.count()).isEqualTo(related.wishlistCount());
        assertThat(tradeRepository.count()).isEqualTo(related.tradeCount());
    }

    private record RelatedData(Long wishlistId, Long tradeId, Long buyerId, TradeStatus status,
                               long wishlistCount, long tradeCount) {}

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
        assertProductFieldsUnchanged(saved, original);
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    private void assertProductFieldsUnchanged(Product saved, Product original) {
        assertThat(saved.getId()).isEqualTo(original.getId());
        assertThat(saved.getSeller().getId()).isEqualTo(original.getSeller().getId());
        assertThat(saved.getTitle()).isEqualTo("Original title");
        assertThat(saved.getDescription()).isEqualTo("Original description");
        assertThat(saved.getPrice()).isEqualTo(1000L);
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
