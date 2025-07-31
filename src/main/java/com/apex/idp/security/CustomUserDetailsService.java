package com.apex.idp.security;

import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
// Add required imports
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom implementation of UserDetailsService for loading user details.
 * In production, this would load users from a database using a UserRepository.
 *
 * This implementation uses an in-memory store for demonstration purposes.
 * Production implementations should:
 * - Load users from a database
 * - Implement proper password hashing
 * - Support user roles and permissions
 * - Handle account states (locked, expired, etc.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;

    // Thread-safe in-memory user store (replace with UserRepository in production)
    private final Map<String, UserInfo> users = new ConcurrentHashMap<>();

    /**
     * Initialize with default users for testing
     * In production, this would not be needed as users would be in the database
     */
    @PostConstruct
    private void initializeDefaultUsers() {
        log.info("Initializing default users for authentication");

        // Admin user
        createUser(UserInfo.builder()
                .id(UUID.randomUUID().toString())
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .email("admin@apex.com")
                .role("ADMIN")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build());

        // Manager user
        createUser(UserInfo.builder()
                .id(UUID.randomUUID().toString())
                .username("manager")
                .password(passwordEncoder.encode("manager123"))
                .email("manager@apex.com")
                .role("MANAGER")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build());

        // Regular user
        createUser(UserInfo.builder()
                .id(UUID.randomUUID().toString())
                .username("user")
                .password(passwordEncoder.encode("user123"))
                .email("user@apex.com")
                .role("USER")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build());

        // Test user
        createUser(UserInfo.builder()
                .id(UUID.randomUUID().toString())
                .username("test")
                .password(passwordEncoder.encode("test123"))
                .email("test@apex.com")
                .role("USER")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build());

        log.info("Initialized {} default users", users.size());
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);

        UserInfo userInfo = users.get(username.toLowerCase());
        if (userInfo == null) {
            log.error("User not found: {}", username);
            throw new UsernameNotFoundException("User not found: " + username);
        }

        // Build authorities from roles
        List<SimpleGrantedAuthority> authorities = buildAuthorities(userInfo.getRole());

        // Create Spring Security User object
        return User.builder()
                .username(userInfo.getUsername())
                .password(userInfo.getPassword())
                .authorities(authorities)
                .disabled(!userInfo.isEnabled())
                .accountExpired(!userInfo.isAccountNonExpired())
                .accountLocked(!userInfo.isAccountNonLocked())
                .credentialsExpired(!userInfo.isCredentialsNonExpired())
                .build();
    }

    /**
     * Create a new user (for testing purposes)
     * In production, this would be handled by a UserService
     */
    public void createUser(UserInfo userInfo) {
        log.info("Creating user: {}", userInfo.getUsername());
        users.put(userInfo.getUsername().toLowerCase(), userInfo);
    }

    /**
     * Update user password
     * In production, this would update the database
     */
    public void updatePassword(String username, String newPassword) {
        UserInfo userInfo = users.get(username.toLowerCase());
        if (userInfo != null) {
            userInfo.setPassword(passwordEncoder.encode(newPassword));
            log.info("Updated password for user: {}", username);
        }
    }

    /**
     * Check if user exists
     */
    public boolean userExists(String username) {
        return users.containsKey(username.toLowerCase());
    }

    /**
     * Get user by username (for internal use)
     */
    public Optional<UserInfo> getUserByUsername(String username) {
        return Optional.ofNullable(users.get(username.toLowerCase()));
    }

    /**
     * Enable/disable user
     */
    public void setUserEnabled(String username, boolean enabled) {
        UserInfo userInfo = users.get(username.toLowerCase());
        if (userInfo != null) {
            userInfo.setEnabled(enabled);
            log.info("User {} is now {}", username, enabled ? "enabled" : "disabled");
        }
    }

    /**
     * Build authorities from role string
     * Supports multiple roles separated by comma
     */
    private List<SimpleGrantedAuthority> buildAuthorities(String roleString) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        if (roleString != null && !roleString.isEmpty()) {
            String[] roles = roleString.split(",");
            for (String role : roles) {
                String trimmedRole = role.trim().toUpperCase();
                // Ensure role has ROLE_ prefix for Spring Security
                if (!trimmedRole.startsWith("ROLE_")) {
                    trimmedRole = "ROLE_" + trimmedRole;
                }
                authorities.add(new SimpleGrantedAuthority(trimmedRole));
            }
        }

        // Add default USER role if no roles specified
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return authorities;
    }

    /**
     * Get all users (for admin purposes)
     */
    public List<UserInfo> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    /**
     * Delete user
     */
    public void deleteUser(String username) {
        if (users.remove(username.toLowerCase()) != null) {
            log.info("Deleted user: {}", username);
        }
    }

    /**
     * Internal user info class
     * In production, this would be a JPA entity
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String username;
        private String password;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
        private boolean enabled;
        private boolean accountNonExpired;
        private boolean accountNonLocked;
        private boolean credentialsNonExpired;
        private LocalDateTime createdAt;
        private LocalDateTime lastLoginAt;
        private String createdBy;
        private String lastModifiedBy;

        // Additional fields for production use
        private String phoneNumber;
        private String department;
        private LocalDateTime passwordChangedAt;
        private Integer failedLoginAttempts;
        private LocalDateTime lockedUntil;
    }

    /**
     * Password policy validation
     * In production, implement proper password policies
     */
    public boolean isPasswordValid(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        // Check for at least one uppercase, lowercase, digit, and special character
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}

