package com.apex.idp.interfaces.rest;

import com.apex.idp.application.service.AuthenticationService;
import com.apex.idp.interfaces.dto.LoginRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for authentication operations.
 * Handles user login, logout, and token refresh.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication management APIs")
@Validated
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * Authenticates a user and returns a JWT token.
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and receive JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.getUsername());

        try {
            // Authenticate user
            AuthenticationResult result = authenticationService.authenticate(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
            );

            if (result.isSuccess()) {
                log.info("Login successful for user: {}", loginRequest.getUsername());

                LoginResponse response = new LoginResponse(
                        result.getToken(),
                        result.getRefreshToken(),
                        result.getExpiresIn(),
                        result.getUser()
                );

                return ResponseEntity.ok(response);
            } else {
                log.warn("Login failed for user: {}", loginRequest.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

        } catch (BadCredentialsException e) {
            log.warn("Invalid credentials for user: {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Login error for user: {}", loginRequest.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Logs out the current user.
     */
    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Logout current user and invalidate token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String token = extractToken(request);

        if (token != null) {
            try {
                authenticationService.logout(token);
                log.info("User logged out successfully");

                Map<String, String> response = new HashMap<>();
                response.put("message", "Logout successful");
                return ResponseEntity.ok(response);

            } catch (Exception e) {
                log.error("Logout error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /**
     * Refreshes the authentication token.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Refresh authentication token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid refresh token")
    })
    public ResponseEntity<RefreshTokenResponse> refreshToken(
            @RequestBody RefreshTokenRequest request) {

        try {
            AuthenticationResult result = authenticationService.refreshToken(
                    request.getRefreshToken()
            );

            if (result.isSuccess()) {
                RefreshTokenResponse response = new RefreshTokenResponse(
                        result.getToken(),
                        result.getRefreshToken(),
                        result.getExpiresIn()
                );

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

        } catch (Exception e) {
            log.error("Token refresh error", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Gets the current user's profile.
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get profile of authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User profile retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<UserProfile> getCurrentUser(HttpServletRequest request) {
        String token = extractToken(request);

        if (token != null) {
            try {
                UserProfile profile = authenticationService.getCurrentUser(token);
                return ResponseEntity.ok(profile);
            } catch (Exception e) {
                log.error("Error getting current user", e);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /**
     * Validates if a token is still valid.
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate token", description = "Check if authentication token is valid")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token is valid"),
            @ApiResponse(responseCode = "401", description = "Token is invalid")
    })
    public ResponseEntity<ValidationResponse> validateToken(
            @RequestBody ValidationRequest request) {

        boolean isValid = authenticationService.validateToken(request.getToken());

        ValidationResponse response = new ValidationResponse(isValid);

        return isValid
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // Helper method to extract token from request
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    // Response DTOs

    public static class LoginResponse {
        private final String token;
        private final String refreshToken;
        private final Long expiresIn;
        private final UserProfile user;

        public LoginResponse(String token, String refreshToken, Long expiresIn, UserProfile user) {
            this.token = token;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
            this.user = user;
        }

        // Getters
        public String getToken() { return token; }
        public String getRefreshToken() { return refreshToken; }
        public Long getExpiresIn() { return expiresIn; }
        public UserProfile getUser() { return user; }
    }

    public static class RefreshTokenRequest {
        private String refreshToken;

        // Getters and setters
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class RefreshTokenResponse {
        private final String token;
        private final String refreshToken;
        private final Long expiresIn;

        public RefreshTokenResponse(String token, String refreshToken, Long expiresIn) {
            this.token = token;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }

        // Getters
        public String getToken() { return token; }
        public String getRefreshToken() { return refreshToken; }
        public Long getExpiresIn() { return expiresIn; }
    }

    public static class ValidationRequest {
        private String token;

        // Getters and setters
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    public static class ValidationResponse {
        private final boolean valid;

        public ValidationResponse(boolean valid) {
            this.valid = valid;
        }

        public boolean isValid() { return valid; }
    }

    public static class UserProfile {
        private final Long id;
        private final String username;
        private final String email;
        private final String firstName;
        private final String lastName;
        private final String role;
        private final String department;

        public UserProfile(Long id, String username, String email, String firstName,
                           String lastName, String role, String department) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.role = role;
            this.department = department;
        }

        // Getters
        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getRole() { return role; }
        public String getDepartment() { return department; }
    }

    public static class AuthenticationResult {
        private final boolean success;
        private final String token;
        private final String refreshToken;
        private final Long expiresIn;
        private final UserProfile user;

        public AuthenticationResult(boolean success, String token, String refreshToken,
                                    Long expiresIn, UserProfile user) {
            this.success = success;
            this.token = token;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
            this.user = user;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getToken() { return token; }
        public String getRefreshToken() { return refreshToken; }
        public Long getExpiresIn() { return expiresIn; }
        public UserProfile getUser() { return user; }
    }
}