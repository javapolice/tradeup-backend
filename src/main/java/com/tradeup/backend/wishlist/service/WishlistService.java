package com.tradeup.backend.wishlist.service;

import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.wishlist.domain.Wishlist;
import com.tradeup.backend.wishlist.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "상품을 찾을 수 없습니다."
                ));

        if(product.getSeller().getId().equals(memberId)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "자신의 상품은 찜할 수 없습니다."
            );
        }

        if(wishlistRepository.existsByMemberIdAndProductId(memberId, productId)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "이미 찜한 상품입니다."
            );
        }

        Wishlist wishlist = new Wishlist(member, product);

        return wishlistRepository.save(wishlist);
    }

    @Transactional
    public void deleteWishlist(
            Long wishlistId,
            Long memberId
    ) {
        Wishlist wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "찜을 찾을 수 없습니다."
                ));

        if(!wishlist.getMember().getId().equals(memberId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "자신의 찜만 취소할 수 있습니다."
            );
        }

        wishlistRepository.delete(wishlist);
    }

    public List<Wishlist> getWishlists(Long memberId) {
        return wishlistRepository.findAllByMemberId(memberId);
    }

}
