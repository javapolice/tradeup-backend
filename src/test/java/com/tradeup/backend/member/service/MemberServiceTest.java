package com.tradeup.backend.member.service;

import com.tradeup.backend.member.config.PasswordEncoderConfig;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberServiceTest {

    @Test
    void 회원가입은_인코더로_해시한_비밀번호만_저장한다() {
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = spy(new PasswordEncoderConfig().passwordEncoder());
        MemberService service = new MemberService(repository, encoder);
        when(repository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member member = service.signUp("member@test.com", "member", "signup-password");

        verify(encoder).encode("signup-password");
        verify(repository).save(member);
        assertThat(member.getPasswordHash()).isNotEqualTo("signup-password");
        assertThat(member.getPasswordHash()).matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
        assertThat(encoder.matches("signup-password", member.getPasswordHash())).isTrue();
        assertThat(encoder.matches("wrong-password", member.getPasswordHash())).isFalse();
    }
}
