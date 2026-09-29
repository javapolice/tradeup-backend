package com.tradeup.backend.member.controller;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.dto.MemberCreateRequest;
import com.tradeup.backend.member.dto.MemberResponse;
import com.tradeup.backend.member.service.MemberService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    public MemberResponse signUp(@RequestBody MemberCreateRequest request) {
        Member member = memberService.signUp(
                request.email(),
                request.nickname()
        );
        return MemberResponse.from(member);
    }

    @GetMapping("/{memberId}")
    public MemberResponse getMember(@PathVariable Long memberId) {
        Member member = memberService.getMember(memberId);

        return MemberResponse.from(member);
    }

}
