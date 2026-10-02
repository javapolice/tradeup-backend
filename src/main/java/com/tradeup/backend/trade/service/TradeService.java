package com.tradeup.backend.trade.service;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.domain.TradeStatus;
import com.tradeup.backend.trade.repository.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TradeService {

    private final TradeRepository tradeRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;

    public TradeService(TradeRepository tradeRepository, MemberRepository memberRepository, ProductRepository productRepository) {
        this.tradeRepository = tradeRepository;
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Trade requestTrade(
            Long productId,
            Long buyerId
    ) {
        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        if (product.getSeller().getId().equals(buyerId)) {
            throw new IllegalArgumentException("자신의 상품은 구매할 수 없습니다.");
        }

        if (product.getStatus() != ProductStatus.SELLING) {
            throw new IllegalStateException("판매 중인 상품만 거래를 요청할 수 있습니다.");
        }

        if (tradeRepository.existsByProductIdAndBuyerIdAndStatus(
                productId,
                buyerId,
                TradeStatus.REQUESTED
        )) {
            throw new IllegalArgumentException("이미 거래 요청한 상품입니다.");
        }

        Trade trade = new Trade(product, buyer);

        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade acceptTrade(
            Long tradeId,
            Long sellerId
    ) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new IllegalArgumentException("거래를 찾을 수 없습니다."));

        Product product = trade.getProduct();

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new IllegalArgumentException("상품 판매자만 거래를 수락할 수 있습니다.");
        }

        trade.accept();
        product.reserve();

        List<Trade> requestedTrades = tradeRepository.findAllByProductIdAndStatus(
                product.getId(),
                TradeStatus.REQUESTED
        );

        for (Trade requestedTrade : requestedTrades) {
            requestedTrade.reject();
        }

        return trade;
    }

}
