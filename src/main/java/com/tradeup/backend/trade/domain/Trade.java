package com.tradeup.backend.trade.domain;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.product.domain.Product;
import jakarta.persistence.*;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeStatus status;

    protected Trade() {}

    public Trade(Product product, Member buyer) {
        this.product = product;
        this.buyer = buyer;
        this.status = TradeStatus.REQUESTED;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Member getBuyer() {
        return buyer;
    }

    public TradeStatus getStatus() {
        return status;
    }
}
