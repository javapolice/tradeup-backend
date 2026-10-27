package com.tradeup.backend.product.service;

import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.repository.TradeRepository;
import com.tradeup.backend.wishlist.repository.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final TradeRepository trades = mock(TradeRepository.class);
    private final WishlistRepository wishlists = mock(WishlistRepository.class);
    private final ProductService service = new ProductService(products, members, trades, wishlists);

    @Test
    void 없는_판매자로_등록하면_NOT_FOUND이며_저장하지_않는다() {
        when(members.findById(99L)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.createProduct(99L, "title", "description", 1000L),
                ErrorCode.NOT_FOUND, "판매자를 찾을 수 없습니다.");

        verify(members).findById(99L);
        verifyNoInteractions(products, trades, wishlists);
    }

    @ParameterizedTest
    @ValueSource(strings = {"get", "update", "delete"})
    void 없는_상품은_NOT_FOUND이며_후속_검사를_호출하지_않는다(String action) {
        when(products.findById(99L)).thenReturn(Optional.empty());

        assertBusinessException(() -> {
            switch (action) {
                case "get" -> service.getProduct(99L);
                case "update" -> service.updateProduct(99L, 1L, "changed", "changed", 2000L);
                case "delete" -> service.deleteProduct(99L, 1L);
                default -> throw new AssertionError(action);
            }
        }, ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다.");

        verify(products).findById(99L);
        verifyNoMoreInteractions(products);
        verifyNoInteractions(members, trades, wishlists);
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "delete"})
    void 비판매자는_상품_상태_검사보다_먼저_FORBIDDEN으로_거부된다(String action) {
        Product product = ownedProduct();
        product.reserve();
        when(products.findById(10L)).thenReturn(Optional.of(product));
        String message = action.equals("update")
                ? "상품 판매자만 수정할 수 있습니다." : "상품 판매자만 삭제할 수 있습니다.";

        assertBusinessException(() -> {
            if (action.equals("update")) {
                service.updateProduct(10L, 2L, "changed", "changed", 2000L);
            } else {
                service.deleteProduct(10L, 2L);
            }
        }, ErrorCode.FORBIDDEN, message);

        assertThat(product.getTitle()).isEqualTo("title");
        assertThat(product.getDescription()).isEqualTo("description");
        assertThat(product.getPrice()).isEqualTo(1000L);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.RESERVED);
        verify(products).findById(10L);
        verifyNoMoreInteractions(products);
        verifyNoInteractions(members, trades, wishlists);
    }

    @Test
    void SELLING_상품의_거래_이력은_CONFLICT이며_찜과_상품을_삭제하지_않는다() {
        Product product = ownedProduct();
        when(products.findById(10L)).thenReturn(Optional.of(product));
        when(trades.existsByProductId(10L)).thenReturn(true);

        assertBusinessException(() -> service.deleteProduct(10L, 1L),
                ErrorCode.CONFLICT, "거래 이력이 있는 상품은 삭제할 수 없습니다.");

        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
        verify(products).findById(10L);
        verify(trades).existsByProductId(10L);
        verifyNoMoreInteractions(products, trades);
        verifyNoInteractions(members, wishlists);
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"RESERVED", "SOLD"})
    void 소유자라도_판매중이_아니면_거래_이력_조회_전에_CONFLICT가_발생한다(ProductStatus status) {
        Product product = ownedProduct();
        product.reserve();
        if (status == ProductStatus.SOLD) {
            product.sold();
        }
        when(products.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.deleteProduct(10L, 1L))
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("판매 중인 상품만 삭제할 수 있습니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));

        assertThat(product.getStatus()).isEqualTo(status);
        verify(products).findById(10L);
        verifyNoMoreInteractions(products);
        verifyNoInteractions(members, trades, wishlists);
    }

    private Product ownedProduct() {
        Member seller = mock(Member.class);
        when(seller.getId()).thenReturn(1L);
        return new Product(seller, "title", "description", 1000L);
    }

    private void assertBusinessException(ThrowingCallable action,
                                         ErrorCode code, String message) {
        assertThatThrownBy(action)
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage(message)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode()).isSameAs(code));
    }
}
