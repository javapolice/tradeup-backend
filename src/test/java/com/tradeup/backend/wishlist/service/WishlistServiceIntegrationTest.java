package com.tradeup.backend.wishlist.service;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.wishlist.domain.Wishlist;
import com.tradeup.backend.wishlist.repository.WishlistRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class WishlistServiceIntegrationTest {

    @Autowired
    private WishlistService wishlistService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 자신의_상품은_찜할_수_없다() {
        // given
        Member seller = memberRepository.save(
                new Member("wishlist-seller@test.com", "wishlist-seller")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> wishlistService.addWishlist(seller.getId(), product.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자신의 상품은 찜할 수 없습니다.");

        entityManager.flush();
        entityManager.clear();

        assertThat(wishlistRepository.existsByMemberIdAndProductId(seller.getId(), product.getId()))
                .isFalse();
    }

    @Test
    void 동일_회원은_동일_상품을_중복으로_찜할_수_없다() {
        // given
        Member seller = memberRepository.save(
                new Member("wishlist-seller@test.com", "wishlist-seller")
        );

        Member buyer = memberRepository.save(
                new Member("wishlist-buyer@test.com", "wishlist-buyer")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Wishlist wishlist = wishlistRepository.save(new Wishlist(buyer, product));

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> wishlistService.addWishlist(buyer.getId(), product.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 찜한 상품입니다.");

        entityManager.flush();
        entityManager.clear();

        assertThat(wishlistRepository.findAllByMemberId(buyer.getId()))
                .extracting(Wishlist::getId)
                .containsExactly(wishlist.getId());
    }

    @Test
    void 다른_회원의_찜을_삭제할_수_없고_실제_DB에_유지된다() {
        // given
        Member seller = memberRepository.save(
                new Member("wishlist-seller@test.com", "wishlist-seller")
        );

        Member buyer = memberRepository.save(
                new Member("wishlist-buyer@test.com", "wishlist-buyer")
        );

        Member otherMember = memberRepository.save(
                new Member("wishlist-other@test.com", "wishlist-other")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Wishlist wishlist = wishlistRepository.save(new Wishlist(buyer, product));

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> wishlistService.deleteWishlist(wishlist.getId(), otherMember.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자신의 찜만 취소할 수 있습니다.");

        entityManager.flush();
        entityManager.clear();

        Wishlist savedWishlist = wishlistRepository.findById(wishlist.getId()).orElseThrow();

        assertThat(savedWishlist.getMember().getId()).isEqualTo(buyer.getId());
        assertThat(savedWishlist.getProduct().getId()).isEqualTo(product.getId());
    }
}
