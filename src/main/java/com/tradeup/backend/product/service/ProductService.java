package com.tradeup.backend.product.service;

import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.dto.ProductUpdateRequest;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.repository.TradeRepository;
import com.tradeup.backend.wishlist.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final TradeRepository tradeRepository;
    private final WishlistRepository wishlistRepository;

    public ProductService(ProductRepository productRepository, MemberRepository memberRepository, TradeRepository tradeRepository, WishlistRepository wishlistRepository) {
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.tradeRepository = tradeRepository;
        this.wishlistRepository = wishlistRepository;
    }

    @Transactional
    public Product createProduct(
            Long sellerId,
            String title,
            String description,
            Long price
    ) {
        Member seller = memberRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "판매자를 찾을 수 없습니다."
                ));

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
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "상품을 찾을 수 없습니다."
                ));
    }

    public List<Product> getSellingProducts() {
        return productRepository.findAllByStatus(ProductStatus.SELLING);
    }

    @Transactional
    public Product updateProduct(
            Long productId,
            Long sellerId,
            ProductUpdateRequest request
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "상품을 찾을 수 없습니다."
                ));

        if(!product.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "상품 판매자만 수정할 수 있습니다."
            );
        }

        product.update(
                request.titlePresent(),
                request.title(),
                request.descriptionPresent(),
                request.description(),
                request.pricePresent(),
                request.price()
        );

        return product;
    }

    @Transactional
    public void deleteProduct(
            Long productId,
            Long sellerId
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "상품을 찾을 수 없습니다."
                ));

        if(!product.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "상품 판매자만 삭제할 수 있습니다."
            );
        }

        product.validateDeletable();

        if (tradeRepository.existsByProductId(productId)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "거래 이력이 있는 상품은 삭제할 수 없습니다."
            );
        }

        wishlistRepository.deleteAllByProductId(productId);

        productRepository.delete(product);
    }

}
