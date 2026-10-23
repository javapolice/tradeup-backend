package com.tradeup.backend.product.dto;

public record ProductUpdateRequest(
        String title,
        String description,
        Long price
) {
}
