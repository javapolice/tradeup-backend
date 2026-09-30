package com.tradeup.backend.product.dto;

import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;

public record ProductResponse(
        Long id,
        Long sellerId,
        String title,
        String description,
        Long price,
        ProductStatus status
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getTitle(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus()
        );
    }
}
