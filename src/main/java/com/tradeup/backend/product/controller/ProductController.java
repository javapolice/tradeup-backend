package com.tradeup.backend.product.controller;

import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.dto.ProductCreateRequest;
import com.tradeup.backend.product.dto.ProductResponse;
import com.tradeup.backend.product.dto.ProductUpdateRequest;
import com.tradeup.backend.product.service.ProductService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ProductResponse createProduct(
            @AuthenticationPrincipal Long authenticatedMemberId,
            @Valid @RequestBody ProductCreateRequest request
            ) {
        Product product = productService.createProduct(
                authenticatedMemberId,
                request.title(),
                request.description(),
                request.price()
        );

        return ProductResponse.from(product);
    }

    @SecurityRequirements
    @GetMapping("/{productId}")
    public ProductResponse getProduct(
            @PathVariable Long productId
    ) {
        Product product = productService.getProduct(productId);

        return ProductResponse.from(product);
    }

    @SecurityRequirements
    @GetMapping
    public List<ProductResponse> getSellingProducts() {
        return productService.getSellingProducts()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @PatchMapping("/{productId}")
    public ProductResponse updateProduct(
            @PathVariable Long productId,
            @AuthenticationPrincipal Long authenticatedMemberId,
            @RequestBody JsonNode json
            ) {
        ProductUpdateRequest request = ProductUpdateRequest.from(json);

        Product product = productService.updateProduct(
                productId,
                authenticatedMemberId,
                request
        );

        return ProductResponse.from(product);
    }

    @DeleteMapping("/{productId}")
    public void deleteProduct(
            @PathVariable Long productId,
            @AuthenticationPrincipal Long authenticatedMemberId
    ) {
        productService.deleteProduct(productId, authenticatedMemberId);
    }

}
