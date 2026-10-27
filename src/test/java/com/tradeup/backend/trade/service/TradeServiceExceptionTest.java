package com.tradeup.backend.trade.service;

import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.domain.TradeStatus;
import com.tradeup.backend.trade.repository.TradeRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TradeServiceExceptionTest {
    private final TradeRepository trades = mock(TradeRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final TradeService service = new TradeService(trades, members, products);

    @Test
    void 구매자가_없으면_상품_조회_전에_NOT_FOUND이다() {
        when(members.findById(2L)).thenReturn(Optional.empty());
        assertBusiness(() -> service.requestTrade(10L, 2L), ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");
        verify(members).findById(2L);
        verifyNoMoreInteractions(members);
        verifyNoInteractions(products, trades);
    }

    @Test
    void 상품이_없으면_구매자_조회_후_NOT_FOUND이다() {
        when(members.findById(2L)).thenReturn(Optional.of(mock(Member.class)));
        when(products.findById(10L)).thenReturn(Optional.empty());
        assertBusiness(() -> service.requestTrade(10L, 2L), ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다.");
        InOrder order = inOrder(members, products);
        order.verify(members).findById(2L);
        order.verify(products).findById(10L);
        verifyNoMoreInteractions(members, products);
        verifyNoInteractions(trades);
    }

    @Test
    void 자신의_상품은_판매_상태와_중복_조회_전에_CONFLICT이다() {
        Member seller = mock(Member.class);
        when(seller.getId()).thenReturn(2L);
        Product product = new Product(seller, "title", "description", 1000L);
        product.reserve();
        when(members.findById(2L)).thenReturn(Optional.of(seller));
        when(products.findById(10L)).thenReturn(Optional.of(product));
        assertBusiness(() -> service.requestTrade(10L, 2L), ErrorCode.CONFLICT, "자신의 상품은 구매할 수 없습니다.");
        verifyNoInteractions(trades);
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"RESERVED", "SOLD"})
    void 판매중이_아니면_중복_조회_전에_CONFLICT이다(ProductStatus status) {
        Product product = product();
        product.reserve();
        if (status == ProductStatus.SOLD) product.sold();
        when(members.findById(2L)).thenReturn(Optional.of(mock(Member.class)));
        when(products.findById(10L)).thenReturn(Optional.of(product));
        assertBusiness(() -> service.requestTrade(10L, 2L), ErrorCode.CONFLICT,
                "판매 중인 상품만 거래를 요청할 수 있습니다.");
        verifyNoInteractions(trades);
    }

    @Test
    void 중복_요청은_회원_상품_소유권_상태_검사_후_CONFLICT이다() {
        Member seller = mock(Member.class);
        Product product = mock(Product.class);
        when(members.findById(2L)).thenReturn(Optional.of(mock(Member.class)));
        when(products.findById(10L)).thenReturn(Optional.of(product));
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(1L);
        when(product.getStatus()).thenReturn(ProductStatus.SELLING);
        when(trades.existsByProductIdAndBuyerIdAndStatus(10L, 2L, TradeStatus.REQUESTED)).thenReturn(true);
        assertBusiness(() -> service.requestTrade(10L, 2L), ErrorCode.CONFLICT, "이미 거래 요청한 상품입니다.");
        InOrder order = inOrder(members, products, product, seller, trades);
        order.verify(members).findById(2L);
        order.verify(products).findById(10L);
        order.verify(product).getSeller();
        order.verify(seller).getId();
        order.verify(product).getStatus();
        order.verify(trades).existsByProductIdAndBuyerIdAndStatus(10L, 2L, TradeStatus.REQUESTED);
        verifyNoMoreInteractions(members, products, product, seller, trades);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject", "complete"})
    void 없는_거래의_변경은_NOT_FOUND이며_추가_조회나_쓰기가_없다(String action) {
        when(trades.findById(99L)).thenReturn(Optional.empty());
        assertBusiness(() -> change(action, 99L, 1L), ErrorCode.NOT_FOUND, "거래를 찾을 수 없습니다.");
        verify(trades).findById(99L);
        verifyNoMoreInteractions(trades);
        verifyNoInteractions(members, products);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject", "complete"})
    void 다른_판매자의_변경은_엔티티_상태_변경_전에_FORBIDDEN이다(String action) {
        Trade trade = mock(Trade.class);
        Product product = mock(Product.class);
        Member seller = mock(Member.class);
        when(trades.findById(99L)).thenReturn(Optional.of(trade));
        when(trade.getProduct()).thenReturn(product);
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(1L);
        String verb = switch (action) { case "accept" -> "수락"; case "reject" -> "거절"; default -> "완료"; };
        assertBusiness(() -> change(action, 99L, 2L), ErrorCode.FORBIDDEN,
                "상품 판매자만 거래를 " + verb + "할 수 있습니다.");
        InOrder order = inOrder(trades, trade, product, seller);
        order.verify(trades).findById(99L);
        order.verify(trade).getProduct();
        order.verify(product).getSeller();
        order.verify(seller).getId();
        verifyNoMoreInteractions(trades, trade, product, seller);
        verifyNoInteractions(members, products);
    }

    @Test
    void 없는_상품의_거래_조회는_NOT_FOUND이며_목록을_조회하지_않는다() {
        when(products.findById(10L)).thenReturn(Optional.empty());
        assertBusiness(() -> service.getProductTrades(10L, 1L), ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다.");
        verify(products).findById(10L);
        verifyNoMoreInteractions(products);
        verifyNoInteractions(members, trades);
    }

    @Test
    void 비판매자의_거래_조회는_FORBIDDEN이며_목록을_조회하지_않는다() {
        Product product = product();
        when(products.findById(10L)).thenReturn(Optional.of(product));
        assertBusiness(() -> service.getProductTrades(10L, 2L), ErrorCode.FORBIDDEN,
                "상품 판매자만 거래 내역을 조회할 수 있습니다.");
        verify(products).findById(10L);
        verifyNoMoreInteractions(products);
        verifyNoInteractions(members, trades);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject", "complete"})
    void 정상_상태_변경의_엔티티와_저장소_호출_순서를_유지한다(String action) {
        Trade trade = mock(Trade.class);
        Trade other = mock(Trade.class);
        Product product = mock(Product.class);
        Member seller = mock(Member.class);
        when(trades.findById(99L)).thenReturn(Optional.of(trade));
        when(trade.getProduct()).thenReturn(product);
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(1L);
        if (action.equals("accept")) {
            when(product.getId()).thenReturn(10L);
            when(trades.findAllByProductIdAndStatus(10L, TradeStatus.REQUESTED)).thenReturn(List.of(other));
        }
        assertThat(change(action, 99L, 1L)).isSameAs(trade);
        InOrder order = inOrder(trades, trade, product, seller, other);
        order.verify(trades).findById(99L);
        order.verify(trade).getProduct();
        order.verify(product).getSeller();
        order.verify(seller).getId();
        switch (action) {
            case "accept" -> {
                order.verify(trade).accept();
                order.verify(product).reserve();
                order.verify(product).getId();
                order.verify(trades).findAllByProductIdAndStatus(10L, TradeStatus.REQUESTED);
                order.verify(other).reject();
            }
            case "reject" -> order.verify(trade).reject();
            case "complete" -> { order.verify(trade).complete(); order.verify(product).sold(); }
            default -> throw new AssertionError(action);
        }
        verifyNoMoreInteractions(trades, trade, product, seller, other);
        verifyNoInteractions(members, products);
    }

    private Product product() {
        Member seller = mock(Member.class);
        when(seller.getId()).thenReturn(1L);
        return new Product(seller, "title", "description", 1000L);
    }

    private Trade change(String action, Long id, Long sellerId) {
        return switch (action) {
            case "accept" -> service.acceptTrade(id, sellerId);
            case "reject" -> service.rejectTrade(id, sellerId);
            case "complete" -> service.completeTrade(id, sellerId);
            default -> throw new AssertionError(action);
        };
    }

    private void assertBusiness(ThrowingCallable action, ErrorCode code, String message) {
        assertThatThrownBy(action).isExactlyInstanceOf(BusinessException.class).hasMessage(message)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode()).isSameAs(code));
    }
}
