package com.tradeup.backend.product.domain;

import com.tradeup.backend.member.domain.Member;
import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    protected Product() {}

    public Product(Member seller, String title, String description, Long price) {
        this.seller = seller;
        this.title = title;
        this.description = description;
        this.price = price;
        this.status = ProductStatus.SELLING;
    }

    public void update(
            String title,
            String description,
            Long price
    ) {
        if(status != ProductStatus.SELLING) {
            throw new IllegalStateException("판매 중인 상품만 수정할 수 있습니다.");
        }

        this.title = title;
        this.description = description;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public Member getSeller() {
        return seller;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Long getPrice() {
        return price;
    }

    public ProductStatus getStatus() {
        return status;
    }
}
