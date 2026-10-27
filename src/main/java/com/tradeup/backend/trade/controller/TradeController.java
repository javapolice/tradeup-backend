package com.tradeup.backend.trade.controller;

import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.dto.TradeCreateRequest;
import com.tradeup.backend.trade.dto.TradeResponse;
import com.tradeup.backend.trade.service.TradeService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping("/trades")
    public TradeResponse requestTrade(
            @AuthenticationPrincipal Long authenticatedMemberId,
            @Valid @RequestBody TradeCreateRequest request
    ) {
        Trade trade = tradeService.requestTrade(
                request.productId(),
                authenticatedMemberId
        );

        return TradeResponse.from(trade);
    }

    @PostMapping("/trades/{tradeId}/accept")
    public TradeResponse acceptTrade(
            @PathVariable Long tradeId,
            @AuthenticationPrincipal Long authenticatedMemberId
    ) {
        Trade trade = tradeService.acceptTrade(tradeId, authenticatedMemberId);

        return TradeResponse.from(trade);
    }

    @PostMapping("/trades/{tradeId}/reject")
    public TradeResponse rejectTrade(
            @PathVariable Long tradeId,
            @AuthenticationPrincipal Long authenticatedMemberId
    ) {
        Trade trade = tradeService.rejectTrade(tradeId, authenticatedMemberId);

        return TradeResponse.from(trade);
    }

    @PostMapping("/trades/{tradeId}/complete")
    public TradeResponse completeTrade(
            @PathVariable Long tradeId,
            @AuthenticationPrincipal Long authenticatedMemberId
    ) {
        Trade trade = tradeService.completeTrade(tradeId, authenticatedMemberId);

        return TradeResponse.from(trade);
    }

    @GetMapping("/members/{memberId}/purchases")
    public List<TradeResponse> getPurchases(
            @PathVariable Long memberId,
            @AuthenticationPrincipal Long authenticatedMemberId
    ) {
        if (!memberId.equals(authenticatedMemberId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "본인의 구매 내역만 조회할 수 있습니다."
            );
        }

        return tradeService.getPurchases(authenticatedMemberId)
                .stream()
                .map(TradeResponse::from)
                .toList();
    }

    @GetMapping("/products/{productId}/trades")
    public List<TradeResponse> getProductTrades(
            @PathVariable Long productId,
            @AuthenticationPrincipal Long authenticatedMemberId
    ) {
        return tradeService.getProductTrades(
                        productId,
                        authenticatedMemberId
                )
                .stream()
                .map(TradeResponse::from)
                .toList();
    }
}
