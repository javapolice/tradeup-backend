package com.tradeup.backend.trade.repository;

import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.domain.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findAllByBuyerId(Long buyerId);

    List<Trade> findAllByProductId(Long productId);

    boolean existsByProductIdAndBuyerIdAndStatus(Long productId, Long buyerId, TradeStatus status);
}
