package com.apex.idp.interfaces.rest;

import com.apex.idp.application.service.AuthenticationService;
import com.apex.idp.interfaces.dto.LoginRequest;
import com.apex.idp.interfaces.dto.LoginResponse;
import com.apex.idp.interfaces.dto.RefreshTokenRequest;
import com.apex.idp.interfaces.dto.UserInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for authentication operations.
 * Handles user login, logout, token refresh, and user information.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication management APIs")
@Validated
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Authenticates a user and returns JWT tokens.
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and receive JWT tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "429", description = "Too many login attempts")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest,
                                               HttpServletRequest request) {
        log.info("Login attempt for user: {} from IP: {}",
                loginRequest.getUsername(), getClientIP(request));

        try {
            // Authenticate user
            AuthenticationService.AuthenticationResult result = authenticationService.authenticate(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
            );

            if (result.isSuccess()) {
                log.info("Login successful for user: {}", loginRequest.getUsername());

                LoginResponse response = LoginResponse.builder()
                        .token(result.getToken())
                        .refreshToken(result.getRefreshToken())
                        .expiresIn(result.getExpiresIn())
                        .tokenType("Bearer")
                        .user(result.getUser())
                        .build();

                // Set security headers
                HttpHeaders headers = new HttpHeaders();
                headers.add("X-Content-Type-Options", "nosniff");
                headers.add("X-Frame-Options", "DENY");
                headers.add("X-XSS-Protection", "1; mode=block");

                return ResponseEntity.ok()
                        .headers(headers)
                        .body(response);
            } else {
                log.warn("Login failed for user: {} - {}",
                        loginRequest.getUsername(), result.getErrorMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.error("Invalid credentials"));
            }

        } catch (BadCredentialsException e) {
            log.warn("Invalid credentials for user: {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.error("Invalid username or password"));
        } catch (Exception e) {
            log.error("Login error for user: {}", loginRequest.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.error("An error occurred during login"));
        }
    }

    /**
     * Refreshes authentication tokens.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid refresh token"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    public ResponseEntity<LoginResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest refreshRequest) {
        log.debug("Token refresh request received");

        try {
            AuthenticationService.AuthenticationResult result =
                    authenticationService.refreshToken(refreshRequest.getRefreshToken());

            if (result.isSuccess()) {
                LoginResponse response = LoginResponse.builder()
                        .token(result.getToken())
                        .refreshToken(result.getRefreshToken())
                        .expiresIn(result.getExpiresIn())
                        .tokenType("Bearer")
                        .build();

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.error("Invalid refresh token"));
            }

        } catch (Exception e) {
            log.error("Token refresh error", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.error("Failed to refresh token"));
        }
    }

    /**
     * Logs out the current user.
     */
    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Logout current user and invalidate tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            HttpServletResponse response) {

        log.info("Logout request for user: {}", userDetails.getUsername());

        try {
            // Extract token from header
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            // Invalidate token
            authenticationService.logout(userDetails.getUsername(), token);

            // Clear any cookies if used
            response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"");

            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("message", "Logout successful");
            responseBody.put("timestamp", String.valueOf(System.currentTimeMillis()));

            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            log.error("Logout error for user: {}", userDetails.getUsername(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Logout failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Gets current user information.
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get information about the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserInfoResponse> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails,
            Authentication authentication) {

        log.debug("Getting user info for: {}", userDetails.getUsername());

        UserInfoResponse userInfo = UserInfoResponse.builder()
                .username(userDetails.getUsername())
                .authorities(authentication.getAuthorities().stream()
                        .map(auth -> auth.getAuthority())
                        .collect(Collectors.toList()))
                .enabled(userDetails.isEnabled())
                .accountNonExpired(userDetails.isAccountNonExpired())
                .accountNonLocked(userDetails.isAccountNonLocked())
                .credentialsNonExpired(userDetails.isCredentialsNonExpired())
                .build();

        return ResponseEntity.ok(userInfo);
    }

    /**
     * Validates a token.
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate token", description = "Check if a token is valid")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token is valid"),
            @ApiResponse(responseCode = "401", description = "Token is invalid")
    })
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestParam String token) {

        log.debug("Token validation request");

        try {
            boolean isValid = authenticationService.validateToken(token);

            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            response.put("timestamp", System.currentTimeMillis());

            if (isValid) {
                // Optionally include token claims
                Map<String, Object> claims = authenticationService.getTokenClaims(token);
                response.put("claims", claims);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Token validation error", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("valid", false);
            errorResponse.put("error", "Token validation failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    /**
     * Changes user password.
     */
    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Change password for authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Not authenticated or wrong password")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {

        log.info("Password change request for user: {}", userDetails.getUsername());

        try {
            boolean success = authenticationService.changePassword(
                    userDetails.getUsername(),
                    request.getCurrentPassword(),
                    request.getNewPassword()
            );

            if (success) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Password changed successfully");
                return ResponseEntity.ok(response);
            } else {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Current password is incorrect");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Password change error for user: {}", userDetails.getUsername(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to change password");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Helper methods

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    // Request/Response DTOs

    public static class ChangePasswordRequest {
        @NotBlank(message = "Current password is required")
        private String currentPassword;

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
        private String newPassword;

        // Getters and setters
        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}