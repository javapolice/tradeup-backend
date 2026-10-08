package com.tradeup.backend.product.domain;

import com.tradeup.backend.member.domain.Member;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static final String PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("test-password");

    @Test
    void 판매중인_상품을_예약하면_상태가_RESERVED로_변경된다() {

        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        // when
        product.reserve();

        // then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.RESERVED);

    }

    @Test
    void 예약된_상품은_다시_예약할_수_없다() {
        //given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        product.reserve();

        // when & then
        assertThatThrownBy(() -> product.reserve())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 예약된_상품을_판매완료하면_상태가_SOLD로_변경된다() {
        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        product.reserve();

        // when
        product.sold();

        // then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD);
    }

    @Test
    void 판매중인_상품은_바로_판매완료할_수_없다() {
        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        // when & then
        assertThatThrownBy(() -> product.sold())
                .isInstanceOf(IllegalStateException.class);
    }

}