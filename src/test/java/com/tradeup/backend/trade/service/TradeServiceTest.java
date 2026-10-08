package com.tradeup.backend.trade.service;

import com.tradeup.backend.member.domain.Member;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.trade.domain.Trade;
import com.tradeup.backend.trade.domain.TradeStatus;
import com.tradeup.backend.trade.repository.TradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    private static final String PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("test-password");

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private TradeService tradeService;

    @Test
    void 정상적으로_거래를_요청할_수_있다() {
        // given
        Member seller = mock(Member.class);
        Member buyer = new Member("buyer@test.com", "buyer", PASSWORD_HASH);

        when(seller.getId()).thenReturn(1L);

        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        when(memberRepository.findById(2L))
                .thenReturn(Optional.of(buyer));

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(tradeRepository.existsByProductIdAndBuyerIdAndStatus(
                1L, 2L, TradeStatus.REQUESTED
        )).thenReturn(false);

        when(tradeRepository.save(any(Trade.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Trade result = tradeService.requestTrade(1L, 2L);

        // then
        assertThat(result.getProduct()).isEqualTo(product);
        assertThat(result.getBuyer()).isEqualTo(buyer);
        assertThat(result.getStatus()).isEqualTo(TradeStatus.REQUESTED);

        verify(tradeRepository).save(any(Trade.class));
    }

}