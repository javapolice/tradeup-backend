package com.tradeup.backend.wishlist.repository;

import com.tradeup.backend.wishlist.domain.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);
}
