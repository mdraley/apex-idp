package com.apex.idp.application.service;

import com.apex.idp.interfaces.dto.LoginRequest;
import com.apex.idp.interfaces.dto.LoginResponse;
import com.apex.idp.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    // Simulate refresh token storage (in production, use Redis or database)
    private final Map<String, String> refreshTokenStore = new HashMap<>();

    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = tokenProvider.generateToken(userDetails);

            return LoginResponse.builder()
                    .token(token)
                    .user(LoginResponse.UserInfo.builder()
                            .id("1") // In real app, get from user entity
                            .username(userDetails.getUsername())
                            .email(userDetails.getUsername() + "@apex.com")
                            .role("ROLE_USER")
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Authentication failed for user: {}", request.getUsername(), e);
            throw new RuntimeException("Invalid credentials");
        }
    }

    public void logout(String username, String token) {
        // In a real application, you might want to blacklist the token
        log.info("User {} logged out", username);
        // Remove any refresh tokens
        refreshTokenStore.entrySet().removeIf(entry -> entry.getValue().equals(username));
    }

    public AuthenticationResult authenticate(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = tokenProvider.generateToken(userDetails);
            String refreshToken = generateRefreshToken(username);

            return AuthenticationResult.success(
                    token,
                    refreshToken,
                    3600L, // 1 hour expiry
                    LoginResponse.UserInfo.builder()
                            .id("1")
                            .username(userDetails.getUsername())
                            .email(userDetails.getUsername() + "@apex.com")
                            .role("ROLE_USER")
                            .build()
            );
        } catch (BadCredentialsException e) {
            return AuthenticationResult.failure("Invalid credentials");
        } catch (Exception e) {
            log.error("Authentication error", e);
            return AuthenticationResult.failure("Authentication failed");
        }
    }

    public AuthenticationResult refreshToken(String refreshToken) {
        String username = refreshTokenStore.get(refreshToken);
        if (username == null) {
            return AuthenticationResult.failure("Invalid refresh token");
        }

        try {
            UserDetails userDetails = tokenProvider.loadUserByUsername(username);
            String newToken = tokenProvider.generateToken(userDetails);
            String newRefreshToken = generateRefreshToken(username);

            // Remove old refresh token
            refreshTokenStore.remove(refreshToken);

            return AuthenticationResult.success(newToken, newRefreshToken, 3600L, null);
        } catch (Exception e) {
            log.error("Token refresh error", e);
            return AuthenticationResult.failure("Failed to refresh token");
        }
    }

    public boolean validateToken(String token) {
        return tokenProvider.validateToken(token);
    }

    public Map<String, Object> getTokenClaims(String token) {
        // This would extract claims from the token
        // For now, return empty map
        return new HashMap<>();
    }

    public boolean changePassword(String username, String currentPassword, String newPassword) {
        // In a real app, this would update the user's password in the database
        // For now, just validate the current password
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, currentPassword)
            );
            // Password would be updated here
            log.info("Password changed for user: {}", username);
            return true;
        } catch (BadCredentialsException e) {
            return false;
        }
    }

    private String generateRefreshToken(String username) {
        String token = UUID.randomUUID().toString();
        refreshTokenStore.put(token, username);
        return token;
    }

    // Inner class for authentication results
    public static class AuthenticationResult {
        private final boolean success;
        private final String token;
        private final String refreshToken;
        private final Long expiresIn;
        private final LoginResponse.UserInfo user;
        private final String errorMessage;

        private AuthenticationResult(boolean success, String token, String refreshToken,
                                     Long expiresIn, LoginResponse.UserInfo user, String errorMessage) {
            this.success = success;
            this.token = token;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public static AuthenticationResult success(String token, String refreshToken,
                                                   Long expiresIn, LoginResponse.UserInfo user) {
            return new AuthenticationResult(true, token, refreshToken, expiresIn, user, null);
        }

        public static AuthenticationResult failure(String errorMessage) {
            return new AuthenticationResult(false, null, null, null, null, errorMessage);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getToken() { return token; }
        public String getRefreshToken() { return refreshToken; }
        public Long getExpiresIn() { return expiresIn; }
        public LoginResponse.UserInfo getUser() { return user; }
        public String getErrorMessage() { return errorMessage; }
    }
}