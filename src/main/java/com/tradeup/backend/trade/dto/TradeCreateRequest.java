package com.tradeup.backend.trade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TradeCreateRequest(

        @NotNull
        @Positive
        Long productId
) {
}
