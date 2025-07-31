package com.apex.idp.interfaces.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO for returning count statistics.
 * Used across various endpoints to provide consistent count responses.
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
     * Constructor for simple count response (for backward compatibility)
     * This constructor is used by controllers when creating count responses
     *
     * @param count The count value
     */
    public CountResponseDTO(Long count) {
        this.count = count;
        this.timestamp = LocalDateTime.now();
        this.label = null;
        this.metadata = new HashMap<>();
    }

    /**
     * Constructor with count and label
     *
     * @param count The count value
     * @param label Descriptive label for the count
     */
    public CountResponseDTO(Long count, String label) {
        this.count = count;
        this.label = label;
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
    }

    /**
     * Static factory method for simple count response
     *
     * @param count The count value
     * @return CountResponseDTO instance
     */
    public static CountResponseDTO of(Long count) {
        return CountResponseDTO.builder()
                .count(count)
                .timestamp(LocalDateTime.now())
                .metadata(new HashMap<>())
                .build();
    }

    /**
     * Static factory method for count response with label
     *
     * @param count The count value
     * @param label Descriptive label
     * @return CountResponseDTO instance
     */
    public static CountResponseDTO of(Long count, String label) {
        return CountResponseDTO.builder()
                .count(count)
                .label(label)
                .timestamp(LocalDateTime.now())
                .metadata(new HashMap<>())
                .build();
    }

    /**
     * Static factory method for count response with metadata
     *
     * @param count The count value
     * @param label Descriptive label
     * @param metadata Additional metadata
     * @return CountResponseDTO instance
     */
    public static CountResponseDTO of(Long count, String label, Map<String, Object> metadata) {
        return CountResponseDTO.builder()
                .count(count)
                .label(label)
                .timestamp(LocalDateTime.now())
                .metadata(metadata != null ? metadata : new HashMap<>())
                .build();
    }

    /**
     * Add metadata entry
     *
     * @param key Metadata key
     * @param value Metadata value
     */
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Get formatted timestamp string
     *
     * @return ISO formatted timestamp
     */
    public String getFormattedTimestamp() {
        return timestamp != null ? timestamp.toString() : null;
    }

    /**
     * Check if count is zero
     *
     * @return true if count is zero or null
     */
    public boolean isEmpty() {
        return count == null || count == 0L;
    }

    /**
     * Get count as primitive long (with null safety)
     *
     * @return count value or 0 if null
     */
    public long getCountValue() {
        return count != null ? count : 0L;
    }

    @Override
    public String toString() {
        return String.format("CountResponseDTO{count=%d, label='%s', timestamp=%s}",
                count, label, timestamp);
    }
}