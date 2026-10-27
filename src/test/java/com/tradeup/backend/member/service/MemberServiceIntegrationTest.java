package com.tradeup.backend.member.service;

import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.domain.Member;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.tradeup.backend.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MemberServiceIntegrationTest {

    private static final String PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("test-password");

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 회원가입_비밀번호는_BCrypt_해시로_저장된다() {
        String password = "signup-password";
        Member member = memberService.signUp("signup@test.com", "signup-member", password);

        entityManager.flush();
        entityManager.clear();

        Member savedMember = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(savedMember.getPasswordHash()).isNotEqualTo(password);
        assertThat(savedMember.getPasswordHash()).matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
        assertThat(passwordEncoder.matches(password, savedMember.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", savedMember.getPasswordHash())).isFalse();
    }

    @Test
    void 비밀번호_해시가_NULL인_기존_회원을_조회할_수_있다() {
        entityManager.createNativeQuery("INSERT INTO members (email, nickname) VALUES (:email, :nickname)")
                .setParameter("email", "legacy@test.com")
                .setParameter("nickname", "legacy-member")
                .executeUpdate();
        entityManager.clear();

        Long memberId = ((Number) entityManager.createNativeQuery(
                        "SELECT id FROM members WHERE email = :email")
                .setParameter("email", "legacy@test.com")
                .getSingleResult()).longValue();

        Member member = memberService.getMember(memberId);
        assertThat(member.getEmail()).isEqualTo("legacy@test.com");
        assertThat(member.getPasswordHash()).isNull();
    }

    @Test
    void 이미_존재하는_이메일로_회원가입할_수_없다() {
        // given
        Member member = memberRepository.save(
                new Member("member-duplicate@test.com", "existing-member", PASSWORD_HASH)
        );

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> memberService.signUp(member.getEmail(), "new-member", "test-password"))
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("이미 사용 중인 이메일입니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));

        entityManager.flush();
        entityManager.clear();

        assertThat(memberRepository.existsByNickname("new-member")).isFalse();
    }

    @Test
    void 이미_존재하는_닉네임으로_회원가입할_수_없다() {
        // given
        Member member = memberRepository.save(
                new Member("member-duplicate@test.com", "existing-member", PASSWORD_HASH)
        );

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> memberService.signUp("new-member@test.com", member.getNickname(), "test-password"))
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("이미 사용 중인 닉네임입니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));

        entityManager.flush();
        entityManager.clear();

        assertThat(memberRepository.existsByEmail("new-member@test.com")).isFalse();
    }

    @Test
    void DB에_없는_회원_조회는_NOT_FOUND이다() {
        long missingMemberId = Long.MAX_VALUE;
        assertThat(memberRepository.existsById(missingMemberId)).isFalse();

        assertThatThrownBy(() -> memberService.getMember(missingMemberId))
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("회원을 찾을 수 없습니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.NOT_FOUND));
    }
}
