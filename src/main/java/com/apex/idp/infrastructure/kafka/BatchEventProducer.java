package com.apex.idp.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for batch processing events.
 * Publishes events for async processing of document batches.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String BATCH_CREATED_TOPIC = "apex.batch.created";
    private static final String BATCH_OCR_COMPLETED_TOPIC = "apex.batch.ocr.completed";
    private static final String BATCH_ANALYSIS_REQUESTED_TOPIC = "apex.batch.analysis.requested";
    private static final String DOCUMENT_PROCESSING_TOPIC = "apex.document.processing";

    /**
     * Sends batch created event
     */
    public void sendBatchCreatedEvent(String batchId, int documentCount) {
        BatchEvent event = BatchEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .batchId(batchId)
                .eventType("BATCH_CREATED")
                .timestamp(LocalDateTime.now())
                .metadata(Map.of("documentCount", documentCount))
                .build();

        sendEvent(BATCH_CREATED_TOPIC, batchId, event);
    }

    /**
     * Sends batch OCR completed event
     */
    public void sendBatchOCRCompletedEvent(String batchId) {
        BatchEvent event = BatchEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .batchId(batchId)
                .eventType("OCR_COMPLETED")
                .timestamp(LocalDateTime.now())
                .build();

        sendEvent(BATCH_OCR_COMPLETED_TOPIC, batchId, event);
    }

    /**
     * Sends document processing event
     */
    public void sendDocumentProcessingEvent(String documentId, String batchId, String action) {
        DocumentEvent event = DocumentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .documentId(documentId)
                .batchId(batchId)
                .action(action)
                .timestamp(LocalDateTime.now())
                .build();

        sendEvent(DOCUMENT_PROCESSING_TOPIC, documentId, event);
    }

    /**
     * Sends analysis requested event
     */
    public void sendAnalysisRequestedEvent(String batchId, String userId) {
        BatchEvent event = BatchEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .batchId(batchId)
                .eventType("ANALYSIS_REQUESTED")
                .timestamp(LocalDateTime.now())
                .metadata(Map.of("requestedBy", userId))
                .build();

        sendEvent(BATCH_ANALYSIS_REQUESTED_TOPIC, batchId, event);
    }

    /**
     * Generic method to send events to Kafka
     */
    private void sendEvent(String topic, String key, Object event) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Event sent successfully to topic: {} with key: {}", topic, key);
                } else {
                    log.error("Failed to send event to topic: {} with key: {}", topic, key, ex);
                }
            });
        } catch (Exception e) {
            log.error("Error sending event to Kafka topic: {}", topic, e);
        }
    }

    /**
     * Batch event class
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchEvent {
        private String eventId;
        private String batchId;
        private String eventType;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
    }

    /**
     * Document event class
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DocumentEvent {
        private String eventId;
        private String documentId;
        private String batchId;
        private String action;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
    }
}