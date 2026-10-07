package com.tradeup.backend.trade.service;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.domain.TradeStatus;
import com.tradeup.backend.trade.repository.TradeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class TradeServiceIntegrationTest {

    @Autowired
    private TradeService tradeService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void  contextLoads() {
    }

    @Test
    void 회원을_실제_DB에_저장할_수_있다() {
        // given
        Member member = new Member(
                "integration@test.com",
                "integration"
        );

        // when
        Member savedMember = memberRepository.save(member);

        // then
        assertThat(savedMember.getId()).isNotNull();
    }

    @Test
    void 실제_DB에서_거래를_요청할_수_있다() {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller")
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        // when
        Trade trade = tradeService.requestTrade(
                product.getId(),
                buyer.getId()
        );

        // then
        assertThat(trade.getId()).isNotNull();
        assertThat(trade.getProduct().getId()).isEqualTo(product.getId());
        assertThat(trade.getBuyer().getId()).isEqualTo(buyer.getId());
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.REQUESTED);
    }

    @Test
    void 거래를_수락하면_상품이_예약되고_다른_요청은_거절된다() {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller")
        );

        Member buyerA = memberRepository.save(
                new Member("buyerA@test.com", "buyerA")
        );

        Member buyerB = memberRepository.save(
                new Member("buyerB@test.com", "buyerB")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Trade tradeA = tradeService.requestTrade(
                product.getId(),
                buyerA.getId()
        );

        Trade tradeB = tradeService.requestTrade(
                product.getId(),
                buyerB.getId()
        );

        // when
        tradeService.acceptTrade(
                tradeA.getId(),
                seller.getId()
        );

        // then
        assertThat(tradeA.getStatus())
                .isEqualTo(TradeStatus.ACCEPTED);

        assertThat(tradeB.getStatus())
                .isEqualTo(TradeStatus.REJECTED);

        assertThat(product.getStatus())
                .isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void 수락된_거래를_완료하면_거래와_상품이_판매완료된다() {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller")
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Trade trade = tradeService.requestTrade(
                product.getId(),
                buyer.getId()
        );

        tradeService.acceptTrade(
                trade.getId(),
                seller.getId()
        );

        // when
        tradeService.completeTrade(
                trade.getId(),
                seller.getId()
        );

        // then
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.COMPLETED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD);
    }

    @Test
    void 거절된_거래는_같은_구매자가_다시_요청할_수_있다() {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller")
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Trade firstTrade = tradeService.requestTrade(
                product.getId(),
                buyer.getId()
        );

        tradeService.rejectTrade(
                firstTrade.getId(),
                seller.getId()
        );

        // when
        Trade secondTrade = tradeService.requestTrade(
                product.getId(),
                buyer.getId()
        );

        // then
        assertThat(firstTrade.getStatus()).isEqualTo(TradeStatus.REJECTED);
        assertThat(secondTrade.getStatus()).isEqualTo(TradeStatus.REQUESTED);
        assertThat(secondTrade.getId()).isNotEqualTo(firstTrade.getId());
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    void 같은_구매자는_같은_상품에_활성_거래를_중복_요청할_수_없다() {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller")
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        tradeService.requestTrade(
                product.getId(),
                buyer.getId()
        );

        // when & then
        assertThatThrownBy(() ->
                tradeService.requestTrade(
                        product.getId(),
                        buyer.getId()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 거래 요청한 상품입니다.");
    }

    @Test
    void 판매자가_아니면_거래를_수락할_수_없다() {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller")
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer")
        );

        Member otherMember = memberRepository.save(
                new Member("other@test.com", "other")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Trade trade = tradeService.requestTrade(
                product.getId(),
                buyer.getId()
        );

        // when & then
        assertThatThrownBy(() ->
                tradeService.acceptTrade(
                        trade.getId(),
                        otherMember.getId()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 판매자만 거래를 수락할 수 있습니다.");

        assertThat(trade.getStatus()).isEqualTo(TradeStatus.REQUESTED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    void 판매자는_자신의_상품에_거래를_요청할_수_없고_실제_DB에_거래가_생성되지_않는다() {
        // given
        Member seller = memberRepository.save(
                new Member("self-trade-seller@test.com", "self-trade-seller")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> tradeService.requestTrade(product.getId(), seller.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자신의 상품은 구매할 수 없습니다.");

        entityManager.flush();
        entityManager.clear();

        assertThat(tradeRepository.findAllByProductId(product.getId())).isEmpty();
    }

    @Test
    void 거래_완료_상태가_실제_DB에_반영된다() {
        // given
        Member seller = memberRepository.save(
                new Member("seller@test.com", "seller")
        );

        Member buyer = memberRepository.save(
                new Member("buyer@test.com", "buyer")
        );

        Product product = productRepository.save(
                new Product(seller, "테스트 상품", "테스트 설명", 10000L)
        );

        Trade trade = tradeService.requestTrade(
                product.getId(),
                buyer.getId()
        );

        tradeService.acceptTrade(
                trade.getId(),
                seller.getId()
        );

        tradeService.completeTrade(
                trade.getId(),
                seller.getId()
        );

        entityManager.flush();
        entityManager.clear();

        // when
        Trade savedTrade = tradeRepository.findById(trade.getId()).orElseThrow();

        Product savedProduct = productRepository.findById(product.getId()).orElseThrow();

        // then
        assertThat(savedTrade.getStatus()).isEqualTo(TradeStatus.COMPLETED);
        assertThat(savedProduct.getStatus()).isEqualTo(ProductStatus.SOLD);
    }

}
