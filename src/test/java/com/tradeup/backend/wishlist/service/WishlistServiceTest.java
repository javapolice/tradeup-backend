package com.tradeup.backend.wishlist.service;

import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.wishlist.domain.Wishlist;
import com.tradeup.backend.wishlist.repository.WishlistRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WishlistServiceTest {

    private final WishlistRepository wishlists = mock(WishlistRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final WishlistService service = new WishlistService(wishlists, members, products);

    @Test
    void 없는_회원은_상품_조회_전에_NOT_FOUND이며_저장하지_않는다() {
        when(members.findById(1L)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.addWishlist(1L, 10L),
                ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");

        verify(members).findById(1L);
        verifyNoMoreInteractions(members);
        verifyNoInteractions(products, wishlists);
    }

    @Test
    void 없는_상품은_회원_조회_후_NOT_FOUND이며_저장하지_않는다() {
        Member member = mock(Member.class);
        when(members.findById(1L)).thenReturn(Optional.of(member));
        when(products.findById(10L)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.addWishlist(1L, 10L),
                ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다.");

        InOrder order = inOrder(members, products);
        order.verify(members).findById(1L);
        order.verify(products).findById(10L);
        verifyNoMoreInteractions(members, products);
        verifyNoInteractions(member, wishlists);
    }

    @Test
    void 자신의_상품은_중복_조회_전에_CONFLICT이며_저장하지_않는다() {
        Member seller = mock(Member.class);
        when(seller.getId()).thenReturn(1L);
        Product product = new Product(seller, "title", "description", 1000L);
        when(members.findById(1L)).thenReturn(Optional.of(seller));
        when(products.findById(10L)).thenReturn(Optional.of(product));

        assertBusinessException(() -> service.addWishlist(1L, 10L),
                ErrorCode.CONFLICT, "자신의 상품은 찜할 수 없습니다.");

        InOrder order = inOrder(members, products, seller);
        order.verify(members).findById(1L);
        order.verify(products).findById(10L);
        order.verify(seller).getId();
        verifyNoMoreInteractions(members, products, seller);
        verifyNoInteractions(wishlists);
    }

    @Test
    void 중복은_회원_상품_소유자_검사_후_CONFLICT이며_저장하지_않는다() {
        Member member = mock(Member.class);
        Member seller = mock(Member.class);
        Product product = mock(Product.class);
        when(members.findById(1L)).thenReturn(Optional.of(member));
        when(products.findById(10L)).thenReturn(Optional.of(product));
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(2L);
        when(wishlists.existsByMemberIdAndProductId(1L, 10L)).thenReturn(true);

        assertBusinessException(() -> service.addWishlist(1L, 10L),
                ErrorCode.CONFLICT, "이미 찜한 상품입니다.");

        InOrder order = inOrder(members, products, product, seller, wishlists);
        order.verify(members).findById(1L);
        order.verify(products).findById(10L);
        order.verify(product).getSeller();
        order.verify(seller).getId();
        order.verify(wishlists).existsByMemberIdAndProductId(1L, 10L);
        verifyNoMoreInteractions(members, products, product, seller, wishlists);
        verifyNoInteractions(member);
    }

    @Test
    void 없는_찜은_NOT_FOUND이며_삭제하지_않는다() {
        when(wishlists.findById(99L)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.deleteWishlist(99L, 1L),
                ErrorCode.NOT_FOUND, "찜을 찾을 수 없습니다.");

        verify(wishlists).findById(99L);
        verifyNoMoreInteractions(wishlists);
        verifyNoInteractions(members, products);
    }

    @Test
    void 타인의_찜은_존재_확인_후_FORBIDDEN이며_삭제하지_않는다() {
        Wishlist wishlist = mock(Wishlist.class);
        Member owner = mock(Member.class);
        when(wishlists.findById(99L)).thenReturn(Optional.of(wishlist));
        when(wishlist.getMember()).thenReturn(owner);
        when(owner.getId()).thenReturn(1L);

        assertBusinessException(() -> service.deleteWishlist(99L, 2L),
                ErrorCode.FORBIDDEN, "자신의 찜만 취소할 수 있습니다.");

        InOrder order = inOrder(wishlists, wishlist, owner);
        order.verify(wishlists).findById(99L);
        order.verify(wishlist).getMember();
        order.verify(owner).getId();
        verifyNoMoreInteractions(wishlists, wishlist, owner);
        verifyNoInteractions(members, products);
    }

    private void assertBusinessException(ThrowingCallable action, ErrorCode code, String message) {
        assertThatThrownBy(action)
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage(message)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode()).isSameAs(code));
    }
}
