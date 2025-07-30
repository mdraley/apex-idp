package com.apex.idp.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom implementation of UserDetailsService for loading user details.
 * In production, this would load users from a database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;

    // Temporary in-memory user store (replace with database in production)
    private final Map<String, UserInfo> users = new HashMap<>();

    /**
     * Initialize with some test users
     */
    @PostConstruct
    private void initUsers() {
        // Admin user
        users.put("admin", UserInfo.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .email("admin@apex.com")
                .role("ADMIN")
                .enabled(true)
                .build());

        // Regular user
        users.put("user", UserInfo.builder()
                .username("user")
                .password(passwordEncoder.encode("user123"))
                .email("user@apex.com")
                .role("USER")
                .enabled(true)
                .build());

        // Test user
        users.put("test", UserInfo.builder()
                .username("test")
                .password(passwordEncoder.encode("test123"))
                .email("test@apex.com")
                .role("USER")
                .enabled(true)
                .build());
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);

        UserInfo userInfo = users.get(username);
        if (userInfo == null) {
            log.error("User not found: {}", username);
            throw new UsernameNotFoundException("User not found: " + username);
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + userInfo.getRole()));

        return User.builder()
                .username(userInfo.getUsername())
                .password(userInfo.getPassword())
                .authorities(authorities)
                .enabled(userInfo.isEnabled())
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    /**
     * Internal user info class
     */
    @lombok.Builder
    @lombok.Data
    private static class UserInfo {
        private String username;
        private String password;
        private String email;
        private String role;
        private boolean enabled;
    }
}

// Import for @PostConstruct
import jakarta.annotation.PostConstruct;