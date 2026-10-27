package com.tradeup.backend.wishlist.controller;

import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.wishlist.domain.Wishlist;
import com.tradeup.backend.wishlist.repository.WishlistRepository;
import com.tradeup.backend.wishlist.service.WishlistService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WishlistControllerIntegrationTest {

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
    private WishlistRepository wishlistRepository;

    @Autowired
    private WishlistService wishlistService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void memberId_없이_등록하면_JWT_회원_A가_DB의_찜_소유자로_저장된다() throws Exception {
        Fixture fixture = fixture();
        long before = wishlistRepository.count();

        MvcResult result = mockMvc.perform(post("/api/wishlists")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.memberAId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d}
                                """.formatted(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(fixture.memberAId()))
                .andExpect(jsonPath("$.productId").value(fixture.productId()))
                .andReturn();

        assertStoredWishlist(responseId(result), fixture.memberAId(), fixture.productId());
        assertThat(wishlistRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void JSON의_B_memberId는_무시되고_A의_찜으로_저장된다() throws Exception {
        Fixture fixture = fixture();
        Long existingBWishlistId = createWishlist(fixture.memberBId(), fixture.productId());
        long before = wishlistRepository.count();

        // 실제 Jackson 설정의 알 수 없는 필드 무시 정책을 검증한다.
        MvcResult result = mockMvc.perform(post("/api/wishlists")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.memberAId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"memberId":%d}
                                """.formatted(fixture.productId(), fixture.memberBId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(fixture.memberAId()))
                .andReturn();

        Long createdId = responseId(result);
        assertThat(createdId).isNotEqualTo(existingBWishlistId);
        assertStoredWishlist(createdId, fixture.memberAId(), fixture.productId());
        assertStoredWishlist(existingBWishlistId, fixture.memberBId(), fixture.productId());
        assertThat(wishlistRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void A의_JWT로_memberId_쿼리_없이_본인_찜을_삭제한다() throws Exception {
        Fixture fixture = fixture();
        Long wishlistId = createWishlist(fixture.memberAId(), fixture.productId());
        Long otherId = createWishlist(fixture.memberBId(), fixture.productId());
        long before = wishlistRepository.count();

        mockMvc.perform(delete("/api/wishlists/{wishlistId}", wishlistId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.memberAId())))
                .andExpect(status().isOk());

        flushAndClear();
        assertThat(wishlistRepository.findById(wishlistId)).isEmpty();
        assertStoredWishlist(otherId, fixture.memberBId(), fixture.productId());
        assertThat(wishlistRepository.count()).isEqualTo(before - 1);
    }

    @Test
    void B의_JWT로_A의_찜_삭제는_서비스에서_거부되고_DB에_유지된다() throws Exception {
        Fixture fixture = fixture();
        Long wishlistId = createWishlist(fixture.memberAId(), fixture.productId());
        Long otherId = createWishlist(fixture.memberBId(), fixture.productId());
        long before = wishlistRepository.count();

        // 실제 소유자의 memberId 쿼리를 추가해도 JWT 기반 소유권 검사를 우회할 수 없다.
        assertThat(fixture.memberAId()).isNotEqualTo(fixture.memberBId());
        assertProblem(mockMvc.perform(delete("/api/wishlists/{wishlistId}", wishlistId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.memberBId()))
                        .param("memberId", fixture.memberAId().toString())),
                403, "FORBIDDEN", "자신의 찜만 취소할 수 있습니다.");

        assertStoredWishlist(wishlistId, fixture.memberAId(), fixture.productId());
        assertStoredWishlist(otherId, fixture.memberBId(), fixture.productId());
        assertThat(wishlistRepository.count()).isEqualTo(before);
    }

    @Test
    void JWT_없는_찜_등록은_401이고_DB에_추가되지_않는다() throws Exception {
        Fixture fixture = fixture();
        long before = wishlistRepository.count();

        mockMvc.perform(post("/api/wishlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d}
                                """.formatted(fixture.productId())))
                .andExpect(status().isUnauthorized());

        flushAndClear();
        assertThat(wishlistRepository.count()).isEqualTo(before);
        assertThat(wishlistRepository.existsByMemberIdAndProductId(fixture.memberAId(), fixture.productId())).isFalse();
    }

    @Test
    void JWT_없는_찜_삭제는_401이고_DB에_유지된다() throws Exception {
        Fixture fixture = fixture();
        Long wishlistId = createWishlist(fixture.memberAId(), fixture.productId());
        long before = wishlistRepository.count();

        mockMvc.perform(delete("/api/wishlists/{wishlistId}", wishlistId))
                .andExpect(status().isUnauthorized());

        assertStoredWishlist(wishlistId, fixture.memberAId(), fixture.productId());
        assertThat(wishlistRepository.count()).isEqualTo(before);
    }

    @Test
    void 없는_상품의_찜_등록은_404이고_DB에_추가되지_않는다() throws Exception {
        Fixture fixture = fixture();
        createWishlist(fixture.memberBId(), fixture.productId());
        long missingProductId = Long.MAX_VALUE;
        assertThat(productRepository.existsById(missingProductId)).isFalse();
        long before = wishlistRepository.count();
        List<WishlistRow> original = wishlistState();

        assertProblem(mockMvc.perform(post("/api/wishlists")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.memberAId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d}
                                """.formatted(missingProductId))),
                404, "NOT_FOUND", "상품을 찾을 수 없습니다.");

        flushAndClear();
        assertThat(wishlistRepository.count()).isEqualTo(before);
        assertThat(wishlistState()).isEqualTo(original);
    }

    @Test
    void 정상_서명된_JWT의_회원이_DB에_없으면_등록은_404이고_찜은_보존된다() throws Exception {
        Fixture fixture = fixture();
        createWishlist(fixture.memberBId(), fixture.productId());
        List<WishlistRow> original = wishlistState();
        long missingMemberId = Long.MAX_VALUE;
        assertThat(memberRepository.existsById(missingMemberId)).isFalse();
        assertThat(productRepository.existsById(fixture.productId())).isTrue();

        // 정상 서명 JWT를 사용한다. 본문의 존재하는 memberId로 principal을 바꿀 수 없다.
        assertProblem(mockMvc.perform(post("/api/wishlists")
                        .header(HttpHeaders.AUTHORIZATION, bearer(missingMemberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"memberId":%d}
                                """.formatted(fixture.productId(), fixture.memberAId()))),
                404, "NOT_FOUND", "회원을 찾을 수 없습니다.");

        assertThat(wishlistState()).isEqualTo(original);
    }

    @Test
    void 자신의_상품_등록은_409이고_다른_회원의_찜도_보존된다() throws Exception {
        Fixture fixture = fixture();
        createWishlist(fixture.memberBId(), fixture.productId());
        List<WishlistRow> original = wishlistState();
        Long sellerId = productRepository.findById(fixture.productId()).orElseThrow().getSeller().getId();
        assertThat(sellerId).isNotEqualTo(fixture.memberAId());
        assertThat(wishlistRepository.existsByMemberIdAndProductId(sellerId, fixture.productId())).isFalse();

        assertProblem(mockMvc.perform(post("/api/wishlists")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"memberId":%d}
                                """.formatted(fixture.productId(), fixture.memberAId()))),
                409, "CONFLICT", "자신의 상품은 찜할 수 없습니다.");

        assertThat(wishlistState()).isEqualTo(original);
    }

    @Test
    void 중복_등록은_409이고_기존_찜의_ID와_소유자와_상품이_보존된다() throws Exception {
        Fixture fixture = fixture();
        Long existingId = createWishlist(fixture.memberAId(), fixture.productId());
        createWishlist(fixture.memberBId(), fixture.productId());
        List<WishlistRow> original = wishlistState();

        assertProblem(mockMvc.perform(post("/api/wishlists")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.memberAId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d}
                                """.formatted(fixture.productId()))),
                409, "CONFLICT", "이미 찜한 상품입니다.");

        assertThat(wishlistState()).isEqualTo(original);
        assertStoredWishlist(existingId, fixture.memberAId(), fixture.productId());
    }

    @Test
    void 없는_찜_삭제는_404이고_다른_찜을_삭제하지_않는다() throws Exception {
        Fixture fixture = fixture();
        createWishlist(fixture.memberAId(), fixture.productId());
        createWishlist(fixture.memberBId(), fixture.productId());
        List<WishlistRow> original = wishlistState();
        long missingWishlistId = Long.MAX_VALUE;
        assertThat(wishlistRepository.existsById(missingWishlistId)).isFalse();

        assertProblem(mockMvc.perform(delete("/api/wishlists/{wishlistId}", missingWishlistId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.memberAId()))),
                404, "NOT_FOUND", "찜을 찾을 수 없습니다.");

        assertThat(wishlistState()).isEqualTo(original);
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

    private List<WishlistRow> wishlistState() {
        flushAndClear();
        return wishlistRepository.findAll().stream()
                .map(wishlist -> new WishlistRow(wishlist.getId(), wishlist.getMember().getId(),
                        wishlist.getProduct().getId()))
                .sorted(Comparator.comparing(WishlistRow::id))
                .toList();
    }

    private record WishlistRow(Long id, Long memberId, Long productId) {}

    private Fixture fixture() {
        Member seller = saveMember("seller");
        Member memberA = saveMember("member-a");
        Member memberB = saveMember("member-b");
        Product product = productRepository.save(new Product(seller, "Wishlist product", "test", 1000L));
        flushAndClear();
        return new Fixture(memberA.getId(), memberB.getId(), product.getId());
    }

    private Member saveMember(String name) {
        return memberRepository.save(new Member("wishlist-api-" + name + "@test.com",
                "wishlist-api-" + name, PASSWORD_HASH));
    }

    private Long createWishlist(Long memberId, Long productId) {
        Long id = wishlistService.addWishlist(memberId, productId).getId();
        flushAndClear();
        return id;
    }

    private void assertStoredWishlist(Long wishlistId, Long memberId, Long productId) {
        flushAndClear();
        Wishlist saved = wishlistRepository.findById(wishlistId).orElseThrow();
        assertThat(saved.getMember().getId()).isEqualTo(memberId);
        assertThat(saved.getProduct().getId()).isEqualTo(productId);
    }

    private Long responseId(MvcResult result) {
        Number id = JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(memberId);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private record Fixture(Long memberAId, Long memberBId, Long productId) {}
}
