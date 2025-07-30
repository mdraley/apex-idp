package com.apex.idp.interfaces.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for returning count statistics
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountResponseDTO {
    private Long count;
    private String label;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;

    /**
     * Simple count response
     */
    public static CountResponseDTO of(Long count) {
        return CountResponseDTO.builder()
                .count(count)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Count response with label
     */
    public static CountResponseDTO of(Long count, String label) {
        return CountResponseDTO.builder()
                .count(count)
                .label(label)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

