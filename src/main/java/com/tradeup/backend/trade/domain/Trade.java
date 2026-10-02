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

    public void accept(){
        if (status != TradeStatus.REQUESTED) {
            throw new IllegalStateException("요청 중인 거래만 수락할 수 있습니다.");
        }

        this.status = TradeStatus.ACCEPTED;
    }

    public void reject(){
        if (status != TradeStatus.REQUESTED) {
            throw new IllegalStateException("요청 중인 거래만 거절할 수 있습니다.");
        }

        this.status = TradeStatus.REJECTED;
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
