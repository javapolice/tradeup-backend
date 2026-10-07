package com.tradeup.backend.trade.controller;

import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.dto.TradeCreateRequest;
import com.tradeup.backend.trade.dto.TradeResponse;
import com.tradeup.backend.trade.service.TradeService;
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
            @RequestBody TradeCreateRequest request
    ) {
        Trade trade = tradeService.requestTrade(
                request.productId(),
                request.buyerId()
        );

        return TradeResponse.from(trade);
    }

    @PostMapping("/trades/{tradeId}/accept")
    public TradeResponse acceptTrade(
            @PathVariable Long tradeId,
            @RequestParam Long sellerId
    ) {
        Trade trade = tradeService.acceptTrade(tradeId, sellerId);

        return TradeResponse.from(trade);
    }

    @PostMapping("/trades/{tradeId}/reject")
    public TradeResponse rejectTrade(
            @PathVariable Long tradeId,
            @RequestParam Long sellerId
    ) {
        Trade trade = tradeService.rejectTrade(tradeId, sellerId);

        return TradeResponse.from(trade);
    }

    @PostMapping("/trades/{tradeId}/complete")
    public TradeResponse completeTrade(
            @PathVariable Long tradeId,
            @RequestParam Long sellerId
    ) {
        Trade trade = tradeService.completeTrade(tradeId, sellerId);

        return TradeResponse.from(trade);
    }

    @GetMapping("/members/{memberId}/purchases")
    public List<TradeResponse> getPurchases(
            @PathVariable Long memberId
    ) {
        return tradeService.getPurchases(memberId)
                .stream()
                .map(TradeResponse::from)
                .toList();
    }

    @GetMapping("/products/{productId}/trades")
    public List<TradeResponse> getProductTrades(
            @PathVariable Long productId
    ) {
        return tradeService.getProductTrades(productId)
                .stream()
                .map(TradeResponse::from)
                .toList();
    }
}
