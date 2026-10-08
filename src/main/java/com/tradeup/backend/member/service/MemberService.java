package com.tradeup.backend.member.service;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Member signUp(String email, String nickname, String password) {

        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        if (memberRepository.existsByNickname(nickname)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        Member member = new Member(email, nickname, passwordEncoder.encode(password));

        return memberRepository.save(member);
    }

    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    public Member login(String email, String password) {
        String errorMsg = "이메일 또는 비밀번호가 올바르지 않습니다.";
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException(errorMsg));

        if (member.getPasswordHash() == null) {
            throw new IllegalArgumentException(errorMsg);
        }

        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new IllegalArgumentException(errorMsg);
        }

        return member;
    }
}
