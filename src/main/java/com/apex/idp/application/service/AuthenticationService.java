package com.apex.idp.application.service;

import com.apex.idp.interfaces.dto.LoginRequest;
import com.apex.idp.interfaces.dto.LoginResponse;
import com.apex.idp.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

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

    public void logout(String token) {
        // In a real application, you might want to blacklist the token
        log.info("User logged out");
    }
}