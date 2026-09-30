package com.tradeup.backend.product.repository;

import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByStatus(ProductStatus status);
}
