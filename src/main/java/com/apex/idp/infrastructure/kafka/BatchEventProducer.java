package com.apex.idp.infrastructure.kafka;

import com.apex.idp.domain.batch.Batch;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer for batch processing events.
 * Publishes domain events to appropriate Kafka topics for asynchronous processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Sends batch created event.
     */
    public void sendBatchCreatedEvent(Batch batch) {
        Map<String, Object> event = new HashMap<>();
        event.put("batchId", batch.getId());
        event.put("name", batch.getName());
        event.put("documentCount", batch.getDocumentCount());
        event.put("timestamp", LocalDateTime.now());
        event.put("eventType", "BATCH_CREATED");

        sendEvent("batch.created", batch.getId(), event);
    }

    /**
     * Sends batch OCR completed event.
     */
    public void sendBatchOcrCompletedEvent(Batch batch) {
        Map<String, Object> event = new HashMap<>();
        event.put("batchId", batch.getId());
        event.put("processedCount", batch.getProcessedDocumentCount());
        event.put("failedCount", batch.getFailedDocumentCount());
        event.put("timestamp", LocalDateTime.now());
        event.put("eventType", "BATCH_OCR_COMPLETED");

        sendEvent("batch.ocr.completed", batch.getId(), event);
    }

    /**
     * Sends batch status update event.
     */
    public void sendBatchStatusUpdateEvent(Batch batch) {
        Map<String, Object> event = new HashMap<>();
        event.put("batchId", batch.getId());
        event.put("status", batch.getStatus().toString());
        event.put("timestamp", LocalDateTime.now());
        event.put("eventType", "BATCH_STATUS_UPDATED");

        sendEvent("batch.status.updated", batch.getId(), event);
    }

    /**
     * Sends document processed event.
     */
    public void sendDocumentProcessedEvent(String batchId, String documentId) {
        Map<String, Object> event = new HashMap<>();
        event.put("batchId", batchId);
        event.put("documentId", documentId);
        event.put("timestamp", LocalDateTime.now());
        event.put("eventType", "DOCUMENT_PROCESSED");

        sendEvent("document.processed", documentId, event);
    }

    /**
     * Sends document failed event.
     */
    public void sendDocumentFailedEvent(String batchId, String documentId) {
        Map<String, Object> event = new HashMap<>();
        event.put("batchId", batchId);
        event.put("documentId", documentId);
        event.put("timestamp", LocalDateTime.now());
        event.put("eventType", "DOCUMENT_FAILED");

        sendEvent("document.failed", documentId, event);
    }

    /**
     * Generic method to send events to Kafka.
     */
    private void sendEvent(String topic, String key, Map<String, Object> event) {
        try {
            log.debug("Sending event to topic {} with key {}: {}", topic, key, event);
            kafkaTemplate.send(topic, key, event);
        } catch (Exception e) {
            log.error("Failed to send event to topic {}: {}", topic, e.getMessage(), e);
        }
    }
}