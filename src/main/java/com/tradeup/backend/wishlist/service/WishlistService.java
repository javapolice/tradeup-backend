package com.tradeup.backend.wishlist.service;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.wishlist.domain.Wishlist;
import com.tradeup.backend.wishlist.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;

    public WishlistService(WishlistRepository wishlistRepository, MemberRepository memberRepository, ProductRepository productRepository) {
        this.wishlistRepository = wishlistRepository;
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Wishlist addWishlist(
            Long memberId,
            Long productId
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        if(product.getSeller().getId().equals(memberId)) {
            throw new IllegalArgumentException("자신의 상품은 찜할 수 없습니다.");
        }

        if(wishlistRepository.existsByMemberIdAndProductId(memberId, productId)) {
            throw new IllegalArgumentException("이미 찜한 상품입니다.");
        }

        Wishlist wishlist = new Wishlist(member, product);

        return wishlistRepository.save(wishlist);
    }

}
