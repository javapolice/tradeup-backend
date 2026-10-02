package com.tradeup.backend.trade.repository;

import com.tradeup.backend.trade.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findAllByBuyerId(Long buyerId);

    List<Trade> findAllByProductId(Long productId);
}
