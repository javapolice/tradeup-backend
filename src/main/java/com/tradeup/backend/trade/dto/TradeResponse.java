package com.tradeup.backend.trade.dto;

import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.domain.TradeStatus;

public record TradeResponse(
        Long id,
        Long productId,
        Long buyerId,
        TradeStatus status
) {

    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getProduct().getId(),
                trade.getBuyer().getId(),
                trade.getStatus()
        );
    }
}
