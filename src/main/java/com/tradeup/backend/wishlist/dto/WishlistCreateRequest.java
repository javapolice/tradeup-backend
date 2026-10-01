package com.tradeup.backend.wishlist.dto;

public record WishlistCreateRequest(
        Long memberId,
        Long productId
) {
}
