package com.tradeup.backend.product.controller;

import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.dto.ProductCreateRequest;
import com.tradeup.backend.product.dto.ProductResponse;
import com.tradeup.backend.product.dto.ProductUpdateRequest;
import com.tradeup.backend.product.service.ProductService;
import org.springframework.web.bind.annotation.*;

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
            @RequestBody ProductCreateRequest request
            ) {
        Product product = productService.createProduct(
                request.sellerId(),
                request.title(),
                request.description(),
                request.price()
        );

        return ProductResponse.from(product);
    }

    @GetMapping("/{productId}")
    public ProductResponse getProduct(
            @PathVariable Long productId
    ) {
        Product product = productService.getProduct(productId);

        return ProductResponse.from(product);
    }

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
            @RequestBody ProductUpdateRequest request
            ) {
        Product product = productService.updateProduct(
                productId,
                request.sellerId(),
                request.title(),
                request.description(),
                request.price()
        );

        return ProductResponse.from(product);
    }

}
