package com.tradeup.backend.member.controller;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.dto.LoginRequest;
import com.tradeup.backend.member.dto.MemberResponse;
import com.tradeup.backend.member.service.MemberService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final MemberService memberService;

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/login")
    public MemberResponse login(@RequestBody LoginRequest request) {

        Member member = memberService.login(request.email(), request.password());

        return MemberResponse.from(member);
    }
}
