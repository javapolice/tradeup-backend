package com.tradeup.backend.trade.domain;

import com.tradeup.backend.member.domain.Member;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.tradeup.backend.product.domain.Product;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.*;

class TradeTest {

    private static final String PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("test-password");

    @Test
    void 요청중인_거래를_수락하면_상태가_ACCEPTED로_변경된다() {
        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Member buyer = new Member("buyer@test.com", "buyer", PASSWORD_HASH);

        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        Trade trade = new Trade(product, buyer);

        // when
        trade.accept();

        // then
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.ACCEPTED);
    }

    @Test
    void 요청중인_거래를_거절하면_상태가_REJECTED로_변경된다() {
        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Member buyer = new Member("buyer@test.com", "buyer", PASSWORD_HASH);

        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        Trade trade = new Trade(product, buyer);

        // when
        trade.reject();

        // then
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.REJECTED);
    }

    @Test
    void 수락된_거래를_완료하면_상태가_COMPLETED로_변경된다() {
        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Member buyer = new Member("buyer@test.com", "buyer", PASSWORD_HASH);

        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        Trade trade = new Trade(product, buyer);

        trade.accept();

        // when
        trade.complete();

        // then
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.COMPLETED);
    }

    @Test
    void 요청중인_거래는_바로_완료할_수_없다() {
        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Member buyer = new Member("buyer@test.com", "buyer", PASSWORD_HASH);

        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        Trade trade = new Trade(product, buyer);

        // when & then
        assertThatThrownBy(() -> trade.complete())
                .isInstanceOf(IllegalStateException.class);
    }


}