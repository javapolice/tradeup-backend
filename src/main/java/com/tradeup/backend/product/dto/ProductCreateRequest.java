package com.tradeup.backend.product.dto;

public record ProductCreateRequest(
        String title,
        String description,
        Long price
) {
}
