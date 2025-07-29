package com.apex.idp.infrastructure.kafka;

import com.apex.idp.application.service.AnalysisService;
import com.apex.idp.application.service.BatchService;
import com.apex.idp.application.service.DocumentService;
import com.apex.idp.domain.batch.Batch;
import com.apex.idp.infrastructure.websocket.WebSocketNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kafka listener for batch processing events.
 * Handles asynchronous processing of batch-related events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchEventListener {

    private final BatchService batchService;
    private final DocumentService documentService;
    private final AnalysisService analysisService;
    private final WebSocketNotificationService webSocketNotificationService;
    private final ObjectMapper objectMapper;

    // Thread pool for async processing
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * Handles batch created events.
     */
    @KafkaListener(topics = "${kafka.topics.batch-created}", groupId = "${kafka.consumer.group-id}")
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleBatchCreated(@Payload String message,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   Acknowledgment acknowledgment) {

        log.info("Received batch created event - topic: {}, partition: {}, offset: {}",
                topic, partition, offset);

        try {
            BatchEvent event = objectMapper.readValue(message, BatchEvent.class);
            log.info("Processing batch created event for batch: {}", event.getBatchId());

            // Process batch asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    processBatchDocuments(event.getBatchId());
                } catch (Exception e) {
                    log.error("Error processing batch: {}", event.getBatchId(), e);
                }
            }, executorService);

            // Acknowledge message
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error handling batch created event", e);
            throw new RuntimeException("Failed to process batch created event", e);
        }
    }

    /**
     * Handles OCR completed events.
     */
    @KafkaListener(topics = "${kafka.topics.ocr-completed}", groupId = "${kafka.consumer.group-id}")
    public void handleOCRCompleted(@Payload String message,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   Acknowledgment acknowledgment) {

        log.info("Received OCR completed event");

        try {
            DocumentEvent event = objectMapper.readValue(message, DocumentEvent.class);
            log.info("OCR completed for document: {}", event.getDocumentId());

            // Send WebSocket notification
            webSocketNotificationService.notifyDocumentStatusUpdate(
                    event.getDocumentId(),
                    "OCR_COMPLETED"
            );

            // Check if batch is ready for analysis
            checkBatchReadyForAnalysis(event.getBatchId());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error handling OCR completed event", e);
            throw new RuntimeException("Failed to process OCR completed event", e);
        }
    }

    /**
     * Handles batch OCR completed events.
     */
    @KafkaListener(topics = "${kafka.topics.batch-ocr-completed}", groupId = "${kafka.consumer.group-id}")
    public void handleBatchOCRCompleted(@Payload String message,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        Acknowledgment acknowledgment) {

        log.info("Received batch OCR completed event");

        try {
            BatchEvent event = objectMapper.readValue(message, BatchEvent.class);
            log.info("Batch OCR completed for: {}", event.getBatchId());

            // Trigger batch analysis
            CompletableFuture.runAsync(() -> {
                try {
                    analysisService.analyzeBatch(event.getBatchId());
                } catch (Exception e) {
                    log.error("Error analyzing batch: {}", event.getBatchId(), e);
                }
            }, executorService);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error handling batch OCR completed event", e);
            throw new RuntimeException("Failed to process batch OCR completed event", e);
        }
    }

    /**
     * Handles document processing errors.
     */
    @KafkaListener(topics = "${kafka.topics.processing-error}", groupId = "${kafka.consumer.group-id}")
    public void handleProcessingError(@Payload String message,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      Acknowledgment acknowledgment) {

        log.error("Received processing error event - topic: {}, partition: {}", topic, partition);

        try {
            ErrorEvent event = objectMapper.readValue(message, ErrorEvent.class);

            // Fixed: Convert partition (int) to String for logging
            log.error("Processing error for {} {}: {} - Partition: {}",
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getErrorMessage(),
                    String.valueOf(partition)  // Fixed: Convert int to String
            );

            // Send error notification
            webSocketNotificationService.notifyError(
                    event.getEntityId(),
                    event.getEntityType(),
                    event.getErrorMessage()
            );

            // Handle retry logic
            if (event.getRetryCount() < 3) {
                scheduleRetry(event);
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error handling processing error event", e);
            throw new RuntimeException("Failed to process error event", e);
        }
    }

    // Private helper methods

    private void processBatchDocuments(String batchId) {
        log.info("Processing documents for batch: {}", batchId);

        try {
            // Get batch documents
            List<String> documentIds = batchService.getBatchDocumentIds(batchId);

            // Process each document
            for (String documentId : documentIds) {
                try {
                    documentService.processDocument(documentId);
                } catch (Exception e) {
                    log.error("Error processing document: {}", documentId, e);
                    // Continue with other documents
                }
            }

        } catch (Exception e) {
            log.error("Error processing batch documents: {}", batchId, e);
        }
    }

    private void checkBatchReadyForAnalysis(String batchId) {
        // This would check if all documents in the batch have completed OCR
        // and trigger analysis if ready
        log.debug("Checking if batch {} is ready for analysis", batchId);
    }

    private void scheduleRetry(ErrorEvent event) {
        log.info("Scheduling retry for {} {}", event.getEntityType(), event.getEntityId());

        // Schedule retry with exponential backoff
        int delaySeconds = (int) Math.pow(2, event.getRetryCount()) * 10;

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delaySeconds * 1000L);

                if ("DOCUMENT".equals(event.getEntityType())) {
                    documentService.reprocessDocument(event.getEntityId());
                }

            } catch (Exception e) {
                log.error("Error during retry", e);
            }
        }, executorService);
    }

    // Event classes

    @Data
    public static class BatchEvent {
        private String batchId;
        private String eventType;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
    }

    @Data
    public static class DocumentEvent {
        private String documentId;
        private String batchId;
        private String eventType;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
    }

    @Data
    public static class ErrorEvent {
        private String entityId;
        private String entityType;
        private String errorMessage;
        private int retryCount;
        private LocalDateTime timestamp;
    }
}