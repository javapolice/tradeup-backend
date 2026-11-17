package com.tradeup.backend.product.dto;

import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;

public record ProductListResponse(
        Long id,
        Long sellerId,
        String title,
        String description,
        Long price,
        ProductStatus status,
        String sellerNickname
) {

    public static ProductListResponse from(Product product) {
        return new ProductListResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getTitle(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus(),
                product.getSeller().getNickname()
        );
    }
}
