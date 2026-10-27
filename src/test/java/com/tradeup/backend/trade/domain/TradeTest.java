package com.tradeup.backend.trade.domain;

import com.tradeup.backend.member.domain.Member;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;


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

    @ParameterizedTest
    @EnumSource(value = TradeStatus.class, names = {"REQUESTED", "REJECTED", "COMPLETED"})
    void 수락되지_않은_거래는_완료할_수_없다(TradeStatus status) {
        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Member buyer = new Member("buyer@test.com", "buyer", PASSWORD_HASH);

        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        Trade trade = new Trade(product, buyer);
        moveTo(trade, status);
        TradeSnapshot before = snapshot(trade);

        // when & then
        assertThatThrownBy(() -> trade.complete())
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("수락된 거래만 완료할 수 있습니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));
        assertThat(snapshot(trade)).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = TradeStatus.class, names = {"ACCEPTED", "REJECTED", "COMPLETED"})
    void 요청중이_아닌_거래는_수락할_수_없다(TradeStatus status) {
        Trade trade = tradeAt(status);
        TradeSnapshot before = snapshot(trade);
        assertThatThrownBy(trade::accept)
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("요청 중인 거래만 수락할 수 있습니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));
        assertThat(snapshot(trade)).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = TradeStatus.class, names = {"ACCEPTED", "REJECTED", "COMPLETED"})
    void 요청중이_아닌_거래는_거절할_수_없다(TradeStatus status) {
        Trade trade = tradeAt(status);
        TradeSnapshot before = snapshot(trade);
        assertThatThrownBy(trade::reject)
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("요청 중인 거래만 거절할 수 있습니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));
        assertThat(snapshot(trade)).isEqualTo(before);
    }

    private Trade tradeAt(TradeStatus status) {
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Member buyer = new Member("buyer@test.com", "buyer", PASSWORD_HASH);
        Trade trade = new Trade(new Product(seller, "테스트 상품", "테스트 설명", 10000L), buyer);
        moveTo(trade, status);
        return trade;
    }

    private void moveTo(Trade trade, TradeStatus status) {
        switch (status) {
            case REQUESTED -> { }
            case REJECTED -> trade.reject();
            case ACCEPTED, COMPLETED -> {
                trade.accept();
                trade.getProduct().reserve();
                if (status == TradeStatus.COMPLETED) {
                    trade.complete();
                    trade.getProduct().sold();
                }
            }
        }
        assertThat(trade.getStatus()).isEqualTo(status);
    }

    private TradeSnapshot snapshot(Trade trade) {
        Product product = trade.getProduct();
        return new TradeSnapshot(trade.getId(), product, trade.getBuyer(), trade.getStatus(), product.getId(),
                product.getSeller(), product.getTitle(), product.getDescription(), product.getPrice(), product.getStatus());
    }

    private record TradeSnapshot(Long id, Product product, Member buyer, TradeStatus status, Long productId,
                                 Member seller, String title, String description, Long price, ProductStatus productStatus) {}


}
