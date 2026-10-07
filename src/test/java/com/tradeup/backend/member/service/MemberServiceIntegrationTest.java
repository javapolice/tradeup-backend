package com.tradeup.backend.member.service;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MemberServiceIntegrationTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 이미_존재하는_이메일로_회원가입할_수_없다() {
        // given
        Member member = memberRepository.save(
                new Member("member-duplicate@test.com", "existing-member")
        );

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> memberService.signUp(member.getEmail(), "new-member"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");

        entityManager.flush();
        entityManager.clear();

        assertThat(memberRepository.existsByNickname("new-member")).isFalse();
    }

    @Test
    void 이미_존재하는_닉네임으로_회원가입할_수_없다() {
        // given
        Member member = memberRepository.save(
                new Member("member-duplicate@test.com", "existing-member")
        );

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> memberService.signUp("new-member@test.com", member.getNickname()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 닉네임입니다.");

        entityManager.flush();
        entityManager.clear();

        assertThat(memberRepository.existsByEmail("new-member@test.com")).isFalse();
    }
}
