package com.apex.idp.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String refreshToken;
    private Long expiresIn;
    private String tokenType;
    private UserInfo user;
    private String error;
    private boolean success;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String username;
        private String email;
        private String role;
    }

    /**
     * Factory method for error responses
     */
    public static LoginResponse error(String errorMessage) {
        return LoginResponse.builder()
                .error(errorMessage)
                .success(false)
                .build();
    }

    /**
     * Factory method for successful responses
     */
    public static LoginResponse success(String token, String refreshToken,
                                        Long expiresIn, UserInfo user) {
        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .tokenType("Bearer")
                .user(user)
                .success(true)
                .build();
    }
}