package com.tradeup.backend.member.dto;

public record MemberCreateRequest(
        String email,
        String nickname,
        String password
) {
}
