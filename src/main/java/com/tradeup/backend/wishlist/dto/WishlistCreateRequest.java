package com.tradeup.backend.wishlist.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WishlistCreateRequest(

        @NotNull
        @Positive
        Long productId
) {
}
