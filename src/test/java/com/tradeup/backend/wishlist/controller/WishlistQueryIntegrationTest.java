package com.tradeup.backend.wishlist.controller;

import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.wishlist.domain.Wishlist;
import com.tradeup.backend.wishlist.service.WishlistService;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WishlistQueryIntegrationTest {

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

    // 실제 Service와 DB를 사용하며 Controller에서의 호출 여부만 관찰한다.
    @MockitoSpyBean
    private WishlistService wishlistService;

    @Test
    void A의_JWT로_A의_목록을_조회하면_A의_찜만_반환된다() throws Exception {
        Fixture fixture = fixture();

        MvcResult result = mockMvc.perform(get("/api/members/{memberId}/wishlists", fixture.memberAId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.memberAId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        String json = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Number> wishlistIds = JsonPath.read(json, "$[*].id");
        List<Number> memberIds = JsonPath.read(json, "$[*].memberId");
        List<Number> productIds = JsonPath.read(json, "$[*].productId");
        assertThat(wishlistIds.stream().map(Number::longValue).toList())
                .containsExactlyInAnyOrder(fixture.firstAWishlistId(), fixture.secondAWishlistId())
                .doesNotContain(fixture.bWishlistId());
        assertThat(memberIds.stream().map(Number::longValue).toList())
                .containsOnly(fixture.memberAId()).doesNotContain(fixture.memberBId());
        assertThat(productIds.stream().map(Number::longValue).toList())
                .containsExactlyInAnyOrder(fixture.firstAProductId(), fixture.secondAProductId())
                .doesNotContain(fixture.bProductId());
        verify(wishlistService).getWishlists(fixture.memberAId());
        verifyNoMoreInteractions(wishlistService);
    }

    @Test
    void A의_JWT로_B의_목록을_조회하면_Service_호출_전에_거부된다() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> mockMvc.perform(get("/api/members/{memberId}/wishlists", fixture.memberBId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.memberAId()))))
                .isInstanceOf(ServletException.class)
                .cause()
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("본인의 찜 목록만 조회할 수 있습니다.");

        verifyNoInteractions(wishlistService);
    }

    @Test
    void JWT_없는_찜_목록_조회는_401이고_Service를_호출하지_않는다() throws Exception {
        Fixture fixture = fixture();

        MvcResult result = mockMvc.perform(get("/api/members/{memberId}/wishlists", fixture.memberAId()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        verifyNoInteractions(wishlistService);
    }

    @Test
    void 찜이_없는_회원의_본인_조회는_200과_빈_배열을_반환한다() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(get("/api/members/{memberId}/wishlists", fixture.emptyMemberId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.emptyMemberId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(wishlistService).getWishlists(fixture.emptyMemberId());
        verifyNoMoreInteractions(wishlistService);
    }

    private Fixture fixture() {
        Member seller = saveMember("seller");
        Member memberA = saveMember("member-a");
        Member memberB = saveMember("member-b");
        Member emptyMember = saveMember("empty-member");
        Product first = productRepository.save(new Product(seller, "A first product", "test", 1000L));
        Product second = productRepository.save(new Product(seller, "A second product", "test", 2000L));
        Product third = productRepository.save(new Product(seller, "B product", "test", 3000L));
        Wishlist firstA = wishlistService.addWishlist(memberA.getId(), first.getId());
        Wishlist secondA = wishlistService.addWishlist(memberA.getId(), second.getId());
        Wishlist wishlistB = wishlistService.addWishlist(memberB.getId(), third.getId());
        entityManager.flush();
        entityManager.clear();
        clearInvocations(wishlistService);
        return new Fixture(memberA.getId(), memberB.getId(), emptyMember.getId(), firstA.getId(),
                secondA.getId(), wishlistB.getId(), first.getId(), second.getId(), third.getId());
    }

    private Member saveMember(String name) {
        return memberRepository.save(new Member("wishlist-query-" + name + "@test.com",
                "wishlist-query-" + name, PASSWORD_HASH));
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(memberId);
    }

    private record Fixture(Long memberAId, Long memberBId, Long emptyMemberId, Long firstAWishlistId,
                           Long secondAWishlistId, Long bWishlistId, Long firstAProductId,
                           Long secondAProductId, Long bProductId) {}
}
