package com.tradeup.backend.product.service;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    public ProductService(ProductRepository productRepository, MemberRepository memberRepository) {
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Product createProduct(
            Long sellerId,
            String title,
            String description,
            Long price
    ) {
        Member seller = memberRepository.findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("판매자를 찾을 수 없습니다."));

        Product product = new Product(
                seller,
                title,
                description,
                price
        );

        return productRepository.save(product);
    }

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
    }

    public List<Product> getSellingProducts() {
        return productRepository.findAllByStatus(ProductStatus.SELLING);
    }

    @Transactional
    public Product updateProduct(
            Long productId,
            Long sellerId,
            String title,
            String description,
            Long price
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        if(!product.getSeller().getId().equals(sellerId)) {
            throw new IllegalArgumentException("상품 판매자만 수정할 수 있습니다.");
        }

        product.update(title, description, price);

        return product;
    }

    @Transactional
    public void deleteProduct(
            Long productId,
            Long sellerId
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        if(!product.getSeller().getId().equals(sellerId)) {
            throw new IllegalArgumentException("상품 판매자만 삭제할 수 있습니다.");
        }

        product.validateDeletable();

        productRepository.delete(product);
    }

}
