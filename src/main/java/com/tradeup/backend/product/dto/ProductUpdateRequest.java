package com.tradeup.backend.product.dto;

public record ProductUpdateRequest(
        Long sellerId,
        String title,
        String description,
        Long price
) {
}
