package com.tradeup.backend.wishlist.controller;

import com.tradeup.backend.wishlist.domain.Wishlist;
import com.tradeup.backend.wishlist.dto.WishlistCreateRequest;
import com.tradeup.backend.wishlist.dto.WishlistResponse;
import com.tradeup.backend.wishlist.service.WishlistService;
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
            @RequestBody WishlistCreateRequest request
    ) {
        Wishlist wishlist = wishlistService.addWishlist(
                request.memberId(),
                request.productId()
        );

        return WishlistResponse.from(wishlist);
    }

    @DeleteMapping("/wishlists/{wishlistId}")
    public void deleteWishlist(
            @PathVariable Long wishlistId,
            @RequestParam Long memberId
    ) {
        wishlistService.deleteWishlist(wishlistId, memberId);
    }

    @GetMapping("/members/{memberId}/wishlists")
    public List<WishlistResponse> getWishlists(
            @PathVariable Long memberId
    ) {
        return wishlistService.getWishlists(memberId)
                .stream()
                .map(WishlistResponse::from)
                .toList();
    }

}
