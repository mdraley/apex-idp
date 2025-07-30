package com.apex.idp.application.service;

import com.apex.idp.interfaces.dto.LoginRequest;
import com.apex.idp.interfaces.dto.LoginResponse;
import com.apex.idp.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.expiration:86400000}") // 24 hours default
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days default
    private long refreshExpiration;

    // Thread-safe refresh token storage (in production, use Redis or database)
    private final Map<String, RefreshTokenInfo> refreshTokenStore = new ConcurrentHashMap<>();

    /**
     * Authenticates user and returns authentication result
     */
    public AuthenticationResult authenticate(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = tokenProvider.generateToken(userDetails);
            String refreshToken = generateRefreshToken(username);

            // Build user info
            LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                    .username(userDetails.getUsername())
                    .role(extractPrimaryRole(userDetails))
                    .build();

            return AuthenticationResult.success(token, refreshToken, jwtExpiration, userInfo);

        } catch (BadCredentialsException e) {
            log.warn("Authentication failed for user: {}", username);
            return AuthenticationResult.failure("Invalid credentials");
        } catch (Exception e) {
            log.error("Authentication error for user: {}", username, e);
            return AuthenticationResult.failure("Authentication failed");
        }
    }

    /**
     * Refreshes authentication token
     */
    public AuthenticationResult refreshToken(String refreshToken) {
        RefreshTokenInfo tokenInfo = refreshTokenStore.get(refreshToken);

        if (tokenInfo == null || tokenInfo.isExpired()) {
            log.warn("Invalid or expired refresh token");
            refreshTokenStore.remove(refreshToken);
            return AuthenticationResult.failure("Invalid refresh token");
        }

        try {
            UserDetails userDetails = tokenProvider.loadUserByUsername(tokenInfo.getUsername());
            String newToken = tokenProvider.generateToken(userDetails);
            String newRefreshToken = generateRefreshToken(tokenInfo.getUsername());

            // Remove old refresh token
            refreshTokenStore.remove(refreshToken);

            LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                    .username(userDetails.getUsername())
                    .role(extractPrimaryRole(userDetails))
                    .build();

            return AuthenticationResult.success(newToken, newRefreshToken, jwtExpiration, userInfo);

        } catch (Exception e) {
            log.error("Error refreshing token", e);
            return AuthenticationResult.failure("Failed to refresh token");
        }
    }

    /**
     * Invalidates refresh token (for logout)
     */
    public void invalidateRefreshToken(String refreshToken) {
        refreshTokenStore.remove(refreshToken);
        log.info("Refresh token invalidated");
    }

    /**
     * Generates a new refresh token
     */
    private String generateRefreshToken(String username) {
        String token = UUID.randomUUID().toString();
        RefreshTokenInfo tokenInfo = new RefreshTokenInfo(username, System.currentTimeMillis() + refreshExpiration);
        refreshTokenStore.put(token, tokenInfo);
        return token;
    }

    /**
     * Extracts primary role from user details
     */
    private String extractPrimaryRole(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .findFirst()
                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                .orElse("USER");
    }

    /**
     * Authentication result wrapper
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class AuthenticationResult {
        private final boolean success;
        private final String token;
        private final String refreshToken;
        private final long expiresIn;
        private final LoginResponse.UserInfo user;
        private final String errorMessage;

        public static AuthenticationResult success(String token, String refreshToken,
                                                   long expiresIn, LoginResponse.UserInfo user) {
            return new AuthenticationResult(true, token, refreshToken, expiresIn, user, null);
        }

        public static AuthenticationResult failure(String errorMessage) {
            return new AuthenticationResult(false, null, null, 0, null, errorMessage);
        }
    }

    /**
     * Refresh token information
     */
    @AllArgsConstructor
    @Getter
    private static class RefreshTokenInfo {
        private final String username;
        private final long expiryTime;

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
}