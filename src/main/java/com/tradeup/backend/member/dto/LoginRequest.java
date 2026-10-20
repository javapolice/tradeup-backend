package com.tradeup.backend.member.dto;

public record LoginRequest(
        String email,
        String password
) {
}
