package com.tradeup.backend.product.dto;

public record ProductCreateRequest(
        Long sellerId,
        String title,
        String description,
        Long price
) {
}
