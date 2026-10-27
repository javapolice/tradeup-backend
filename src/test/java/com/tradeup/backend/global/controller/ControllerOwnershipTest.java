package com.tradeup.backend.global.controller;

import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.controller.MemberController;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.dto.MemberResponse;
import com.tradeup.backend.member.service.MemberService;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.trade.controller.TradeController;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.domain.TradeStatus;
import com.tradeup.backend.trade.dto.TradeResponse;
import com.tradeup.backend.trade.service.TradeService;
import com.tradeup.backend.wishlist.controller.WishlistController;
import com.tradeup.backend.wishlist.domain.Wishlist;
import com.tradeup.backend.wishlist.dto.WishlistResponse;
import com.tradeup.backend.wishlist.service.WishlistService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ControllerOwnershipTest {
    private final MemberService members = mock(MemberService.class);
    private final WishlistService wishlists = mock(WishlistService.class);
    private final TradeService trades = mock(TradeService.class);
    private final MemberController memberController = new MemberController(members);
    private final WishlistController wishlistController = new WishlistController(wishlists);
    private final TradeController tradeController = new TradeController(trades);

    enum Endpoint { MEMBER, WISHLIST, PURCHASES }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void 타인_경로_ID는_FORBIDDEN이며_어떤_Service도_호출하지_않는다(Endpoint endpoint) {
        Long pathMemberId = 942L;
        Long authenticatedMemberId = 941L;
        String message = switch (endpoint) {
            case MEMBER -> "본인의 회원 정보만 조회할 수 있습니다.";
            case WISHLIST -> "본인의 찜 목록만 조회할 수 있습니다.";
            case PURCHASES -> "본인의 구매 내역만 조회할 수 있습니다.";
        };

        assertThatThrownBy(() -> query(endpoint, pathMemberId, authenticatedMemberId))
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage(message)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.FORBIDDEN));
        verifyNoInteractions(members, wishlists, trades);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void 본인_조회는_principal_ID를_Service에_전달하고_실제_응답을_변환한다(Endpoint endpoint) {
        Long pathMemberId = Long.valueOf("941");
        Long authenticatedMemberId = Long.valueOf("941");
        assertThat(pathMemberId).isEqualTo(authenticatedMemberId).isNotSameAs(authenticatedMemberId);
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(authenticatedMemberId);
        switch (endpoint) {
            case MEMBER -> {
                when(member.getEmail()).thenReturn("owner@test.com");
                when(member.getNickname()).thenReturn("owner");
                when(members.getMember(authenticatedMemberId)).thenReturn(member);
                assertThat(query(endpoint, pathMemberId, authenticatedMemberId))
                        .isEqualTo(new MemberResponse(authenticatedMemberId, "owner@test.com", "owner"));
                verify(members).getMember(authenticatedMemberId);
                verifyNoMoreInteractions(members);
                verifyNoInteractions(wishlists, trades);
            }
            case WISHLIST -> {
                Product product = mock(Product.class);
                when(product.getId()).thenReturn(5001L);
                Wishlist wishlist = mock(Wishlist.class);
                when(wishlist.getId()).thenReturn(1001L);
                when(wishlist.getMember()).thenReturn(member);
                when(wishlist.getProduct()).thenReturn(product);
                when(wishlists.getWishlists(authenticatedMemberId)).thenReturn(List.of(wishlist));
                assertThat(query(endpoint, pathMemberId, authenticatedMemberId))
                        .isEqualTo(List.of(new WishlistResponse(1001L, authenticatedMemberId, 5001L)));
                verify(wishlists).getWishlists(authenticatedMemberId);
                verifyNoMoreInteractions(wishlists);
                verifyNoInteractions(members, trades);
            }
            case PURCHASES -> {
                Product product = mock(Product.class);
                when(product.getId()).thenReturn(5001L);
                Trade trade = mock(Trade.class);
                when(trade.getId()).thenReturn(1001L);
                when(trade.getProduct()).thenReturn(product);
                when(trade.getBuyer()).thenReturn(member);
                when(trade.getStatus()).thenReturn(TradeStatus.REQUESTED);
                when(trades.getPurchases(authenticatedMemberId)).thenReturn(List.of(trade));
                assertThat(query(endpoint, pathMemberId, authenticatedMemberId))
                        .isEqualTo(List.of(new TradeResponse(1001L, 5001L, authenticatedMemberId, TradeStatus.REQUESTED)));
                verify(trades).getPurchases(authenticatedMemberId);
                verifyNoMoreInteractions(trades);
                verifyNoInteractions(members, wishlists);
            }
        }
    }

    private Object query(Endpoint endpoint, Long pathMemberId, Long principal) {
        return switch (endpoint) {
            case MEMBER -> memberController.getMember(pathMemberId, principal);
            case WISHLIST -> wishlistController.getWishlists(pathMemberId, principal);
            case PURCHASES -> tradeController.getPurchases(pathMemberId, principal);
        };
    }
}
