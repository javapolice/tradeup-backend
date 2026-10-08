package com.tradeup.backend.product.service;

import com.tradeup.backend.member.domain.Member;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.repository.TradeRepository;
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
class ProductServiceIntegrationTest {

    private static final String PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("test-password");

    @Autowired
    private ProductService productService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 거래_이력_없이_찜만_있는_판매중_상품을_삭제하면_상품과_찜이_실제_DB에서_삭제된다() {
        // given
        Member seller = memberRepository.save(
                new Member("product-delete-seller@test.com", "product-delete-seller", PASSWORD_HASH)
        );

        Member buyer = memberRepository.save(
                new Member("product-delete-buyer@test.com", "product-delete-buyer", PASSWORD_HASH)
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Wishlist wishlist = wishlistRepository.save(new Wishlist(buyer, product));

        entityManager.flush();
        entityManager.clear();

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
                .isEqualTo(ProductStatus.SELLING);
        assertThat(wishlistRepository.findById(wishlist.getId())).isPresent();
        assertThat(tradeRepository.existsByProductId(product.getId())).isFalse();

        // when
        productService.deleteProduct(product.getId(), seller.getId());

        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(productRepository.findById(product.getId())).isEmpty();
        assertThat(wishlistRepository.findById(wishlist.getId())).isEmpty();
    }

    @Test
    void 거래_이력이_있는_판매중_상품은_삭제할_수_없고_상품과_거래가_실제_DB에_유지된다() {
        // given
        Member seller = memberRepository.save(
                new Member("product-delete-seller@test.com", "product-delete-seller", PASSWORD_HASH)
        );

        Member buyer = memberRepository.save(
                new Member("product-delete-buyer@test.com", "product-delete-buyer", PASSWORD_HASH)
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Trade trade = tradeRepository.save(new Trade(product, buyer));

        entityManager.flush();
        entityManager.clear();

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
                .isEqualTo(ProductStatus.SELLING);
        assertThat(tradeRepository.findById(trade.getId())).isPresent();

        // when & then
        assertThatThrownBy(() -> productService.deleteProduct(product.getId(), seller.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("거래 이력이 있는 상품은 삭제할 수 없습니다");

        entityManager.flush();
        entityManager.clear();

        assertThat(productRepository.findById(product.getId())).isPresent();
        assertThat(tradeRepository.findById(trade.getId())).isPresent();
    }
}
