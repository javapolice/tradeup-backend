package com.tradeup.backend.trade.dto;

public record TradeCreateRequest(
        Long productId,
        Long buyerId
) {
}
