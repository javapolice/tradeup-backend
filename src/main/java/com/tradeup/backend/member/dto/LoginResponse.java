package com.tradeup.backend.member.dto;

public record LoginResponse(
        String accessToken,
        String tokenType
) {
    public static LoginResponse from(String token) {
        return new LoginResponse(
                token,
                "Bearer"
        );
    }

}
