package com.tradeup.backend.wishlist.dto;

import com.tradeup.backend.wishlist.domain.Wishlist;

public record WishlistResponse(
        Long id,
        Long memberId,
        Long productId
) {
    public static WishlistResponse from(Wishlist wishlist) {
        return new WishlistResponse(
                wishlist.getId(),
                wishlist.getMember().getId(),
                wishlist.getProduct().getId()
        );
    }
}
