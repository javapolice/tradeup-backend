package com.tradeup.backend.wishlist.controller;

import com.tradeup.backend.wishlist.domain.Wishlist;
import com.tradeup.backend.wishlist.dto.WishlistCreateRequest;
import com.tradeup.backend.wishlist.dto.WishlistResponse;
import com.tradeup.backend.wishlist.service.WishlistService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping("/wishlists")
    public WishlistResponse addWishlist(
            @AuthenticationPrincipal Long authenticatedMemberId,
            @RequestBody WishlistCreateRequest request
    ) {
        Wishlist wishlist = wishlistService.addWishlist(
                authenticatedMemberId,
                request.productId()
        );

        return WishlistResponse.from(wishlist);
    }

    @DeleteMapping("/wishlists/{wishlistId}")
    public void deleteWishlist(
            @PathVariable Long wishlistId,
            @AuthenticationPrincipal Long authenticatedMemberId
    ) {
        wishlistService.deleteWishlist(wishlistId, authenticatedMemberId);
    }

    @GetMapping("/members/{memberId}/wishlists")
    public List<WishlistResponse> getWishlists(
            @PathVariable Long memberId,
            @AuthenticationPrincipal Long authenticatedMemberId
    ) {
        if (!memberId.equals(authenticatedMemberId)) {
            throw new IllegalArgumentException("본인의 찜 목록만 조회할 수 있습니다.");
        }

        return wishlistService.getWishlists(authenticatedMemberId)
                .stream()
                .map(WishlistResponse::from)
                .toList();
    }

}
