package com.tradeup.backend.member.service;

import com.tradeup.backend.member.config.PasswordEncoderConfig;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemberServiceTest {

    private static final String LOGIN_FAILURE_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

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

    @Test
    void 올바른_비밀번호로_로그인하면_조회한_회원을_반환한다() {
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = spy(new BCryptPasswordEncoder());
        String passwordHash = encoder.encode("login-password");
        Member member = new Member("member@test.com", "member", passwordHash);
        when(repository.findByEmail("member@test.com")).thenReturn(Optional.of(member));
        MemberService service = new MemberService(repository, encoder);

        Member result = service.login("member@test.com", "login-password");

        assertThat(result).isSameAs(member);
        verify(repository).findByEmail("member@test.com");
        verify(encoder).matches("login-password", passwordHash);
    }

    @Test
    void 비밀번호가_틀리면_공통_로그인_실패_예외가_발생한다() {
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = spy(new BCryptPasswordEncoder());
        String passwordHash = encoder.encode("login-password");
        Member member = new Member("member@test.com", "member", passwordHash);
        when(repository.findByEmail("member@test.com")).thenReturn(Optional.of(member));
        MemberService service = new MemberService(repository, encoder);

        assertThatThrownBy(() -> service.login("member@test.com", "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(LOGIN_FAILURE_MESSAGE);
        verify(encoder).matches("wrong-password", passwordHash);
    }

    @Test
    void 이메일이_없으면_공통_로그인_실패_예외가_발생한다() {
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repository.findByEmail("missing@test.com")).thenReturn(Optional.empty());
        MemberService service = new MemberService(repository, encoder);

        assertThatThrownBy(() -> service.login("missing@test.com", "login-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(LOGIN_FAILURE_MESSAGE);
        verify(repository).findByEmail("missing@test.com");
        verifyNoInteractions(encoder);
    }

    @Test
    void 기존_회원의_해시가_NULL이면_공통_로그인_실패_예외가_발생한다() {
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        // 신규 생성자는 NULL을 금지하므로 기존 DB에서 조회된 회원을 mock으로 표현한다.
        Member legacyMember = mock(Member.class);
        when(legacyMember.getPasswordHash()).thenReturn(null);
        when(repository.findByEmail("legacy@test.com")).thenReturn(Optional.of(legacyMember));
        MemberService service = new MemberService(repository, encoder);

        assertThatThrownBy(() -> service.login("legacy@test.com", "login-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(LOGIN_FAILURE_MESSAGE);
        verify(repository).findByEmail("legacy@test.com");
        verifyNoInteractions(encoder);
    }
}
