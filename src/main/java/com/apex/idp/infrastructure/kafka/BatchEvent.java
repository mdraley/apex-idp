package com.apex.idp.infrastructure.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event class for batch processing notifications.
 * Used for Kafka messaging between services.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchEvent {

    private String batchId;
    private String status;
    private Long timestamp;
    private String details;

    // Additional fields can be added as needed for specific event types
}
