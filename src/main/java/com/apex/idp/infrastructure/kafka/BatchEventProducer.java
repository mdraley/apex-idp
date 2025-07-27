package com.apex.idp.infrastructure.kafka;

import com.apex.idp.domain.batch.Batch.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for batch processing events.
 * Publishes domain events to appropriate Kafka topics for asynchronous processing.
 */
@Component
public class BatchEventProducer {

    private static final Logger log = LoggerFactory.getLogger(BatchEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.batch-created:batch-created}")
    private String batchCreatedTopic;

    @Value("${kafka.topics.batch-ocr-completed:batch-ocr-completed}")
    private String batchOcrCompletedTopic;

    @Value("${kafka.topics.batch-analysis-completed:batch-analysis-completed}")
    private String batchAnalysisCompletedTopic;

    @Value("${kafka.topics.document-processed:document-processed}")
    private String documentProcessedTopic;

    @Value("${kafka.topics.batch-status-changed:batch-status-changed}")
    private String batchStatusChangedTopic;

    public BatchEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a batch created event.
     */
    public CompletableFuture<SendResult<String, String>> publishBatchCreated(
            Long batchId, String batchName, int documentCount) {

        BatchCreatedEvent event = new BatchCreatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                batchId,
                batchName,
                documentCount
        );

        return publishEvent(batchCreatedTopic, batchId.toString(), event, "BatchCreatedEvent");
    }

    /**
     * Publishes a batch OCR completed event.
     */
    public CompletableFuture<SendResult<String, String>> publishBatchOcrCompleted(
            Long batchId, int successfulDocuments, int failedDocuments) {

        BatchOcrCompletedEvent event = new BatchOcrCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                batchId,
                successfulDocuments,
                failedDocuments
        );

        return publishEvent(batchOcrCompletedTopic, batchId.toString(), event,
                "BatchOcrCompletedEvent");
    }

    /**
     * Publishes a batch analysis completed event.
     */
    public CompletableFuture<SendResult<String, String>> publishBatchAnalysisCompleted(
            Long batchId, String summary, String recommendations) {

        BatchAnalysisCompletedEvent event = new BatchAnalysisCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                batchId,
                summary,
                recommendations
        );

        return publishEvent(batchAnalysisCompletedTopic, batchId.toString(), event,
                "BatchAnalysisCompletedEvent");
    }

    /**
     * Publishes a document processed event.
     */
    public CompletableFuture<SendResult<String, String>> publishDocumentProcessed(
            Long documentId, Long batchId, String status, String extractedData) {

        DocumentProcessedEvent event = new DocumentProcessedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                documentId,
                batchId,
                status,
                extractedData
        );

        return publishEvent(documentProcessedTopic, documentId.toString(), event,
                "DocumentProcessedEvent");
    }

    /**
     * Publishes a batch status changed event.
     */
    public CompletableFuture<SendResult<String, String>> publishBatchStatusChanged(
            Long batchId, String oldStatus, String newStatus) {

        BatchStatusChangedEvent event = new BatchStatusChangedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                batchId,
                oldStatus,
                newStatus
        );

        return publishEvent(batchStatusChangedTopic, batchId.toString(), event,
                "BatchStatusChangedEvent");
    }

    /**
     * Generic method to publish events to Kafka.
     */
    private CompletableFuture<SendResult<String, String>> publishEvent(
            String topic, String key, Object event, String eventType) {

        try {
            String payload = objectMapper.writeValueAsString(event);

            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);

            // Add event metadata headers
            record.headers().add(new RecordHeader("eventType",
                    eventType.getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("timestamp",
                    String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("source",
                    "apex-idp".getBytes(StandardCharsets.UTF_8)));

            log.debug("Publishing {} to topic {} with key {}", eventType, topic, key);

            return kafkaTemplate.send(record)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish {} to topic {}: {}",
                                    eventType, topic, ex.getMessage(), ex);
                        } else {
                            log.info("Successfully published {} to topic {} at offset {}",
                                    eventType, topic, result.getRecordMetadata().offset());
                        }
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event {}: {}", eventType, e.getMessage(), e);
            CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    /**
     * Event classes for different batch processing stages.
     */
    public static class BatchCreatedEvent extends DomainEvent {
        private final Long batchId;
        private final String batchName;
        private final int documentCount;

        public BatchCreatedEvent(String eventId, Instant timestamp, Long batchId,
                                 String batchName, int documentCount) {
            super(eventId, timestamp);
            this.batchId = batchId;
            this.batchName = batchName;
            this.documentCount = documentCount;
        }

        // Getters
        public Long getBatchId() { return batchId; }
        public String getBatchName() { return batchName; }
        public int getDocumentCount() { return documentCount; }
    }

    public static class BatchOcrCompletedEvent extends DomainEvent {
        private final Long batchId;
        private final int successfulDocuments;
        private final int failedDocuments;

        public BatchOcrCompletedEvent(String eventId, Instant timestamp, Long batchId,
                                      int successfulDocuments, int failedDocuments) {
            super(eventId, timestamp);
            this.batchId = batchId;
            this.successfulDocuments = successfulDocuments;
            this.failedDocuments = failedDocuments;
        }

        // Getters
        public Long getBatchId() { return batchId; }
        public int getSuccessfulDocuments() { return successfulDocuments; }
        public int getFailedDocuments() { return failedDocuments; }
    }

    public static class BatchAnalysisCompletedEvent extends DomainEvent {
        private final Long batchId;
        private final String summary;
        private final String recommendations;

        public BatchAnalysisCompletedEvent(String eventId, Instant timestamp, Long batchId,
                                           String summary, String recommendations) {
            super(eventId, timestamp);
            this.batchId = batchId;
            this.summary = summary;
            this.recommendations = recommendations;
        }

        // Getters
        public Long getBatchId() { return batchId; }
        public String getSummary() { return summary; }
        public String getRecommendations() { return recommendations; }
    }

    public static class DocumentProcessedEvent extends DomainEvent {
        private final Long documentId;
        private final Long batchId;
        private final String status;
        private final String extractedData;

        public DocumentProcessedEvent(String eventId, Instant timestamp, Long documentId,
                                      Long batchId, String status, String extractedData) {
            super(eventId, timestamp);
            this.documentId = documentId;
            this.batchId = batchId;
            this.status = status;
            this.extractedData = extractedData;
        }

        // Getters
        public Long getDocumentId() { return documentId; }
        public Long getBatchId() { return batchId; }
        public String getStatus() { return status; }
        public String getExtractedData() { return extractedData; }
    }

    public static class BatchStatusChangedEvent extends DomainEvent {
        private final Long batchId;
        private final String oldStatus;
        private final String newStatus;

        public BatchStatusChangedEvent(String eventId, Instant timestamp, Long batchId,
                                       String oldStatus, String newStatus) {
            super(eventId, timestamp);
            this.batchId = batchId;
            this.oldStatus = oldStatus;
            this.newStatus = newStatus;
        }

        // Getters
        public Long getBatchId() { return batchId; }
        public String getOldStatus() { return oldStatus; }
        public String getNewStatus() { return newStatus; }
    }

    /**
     * Base domain event class.
     */
    public static abstract class DomainEvent {
        private final String eventId;
        private final Instant timestamp;

        protected DomainEvent(String eventId, Instant timestamp) {
            this.eventId = eventId;
            this.timestamp = timestamp;
        }

        public String getEventId() { return eventId; }
        public Instant getTimestamp() { return timestamp; }
    }
}