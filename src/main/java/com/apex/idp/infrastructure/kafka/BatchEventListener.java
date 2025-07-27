package com.apex.idp.infrastructure.kafka;

import com.apex.idp.application.service.AnalysisService;
import com.apex.idp.application.service.BatchService;
import com.apex.idp.application.service.DocumentService;
import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.document.Document;
import com.apex.idp.infrastructure.kafka.BatchEventProducer.*;
import com.apex.idp.infrastructure.websocket.WebSocketNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka consumer for processing batch-related events.
 * Handles asynchronous processing of OCR completion, analysis, and status updates.
 */
@Component
public class BatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(BatchEventListener.class);

    private final BatchService batchService;
    private final DocumentService documentService;
    private final AnalysisService analysisService;
    private final WebSocketNotificationService notificationService;
    private final ObjectMapper objectMapper;

    public BatchEventListener(BatchService batchService,
                              DocumentService documentService,
                              AnalysisService analysisService,
                              WebSocketNotificationService notificationService,
                              ObjectMapper objectMapper) {
        this.batchService = batchService;
        this.documentService = documentService;
        this.analysisService = analysisService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles batch OCR completion events.
     * Triggers AI analysis when OCR processing is complete.
     */
    @KafkaListener(
            topics = "${kafka.topics.batch-ocr-completed:batch-ocr-completed}",
            groupId = "${kafka.consumer.group-id:apex-idp-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public void handleBatchOcrCompleted(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received BatchOcrCompleted event from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);

        try {
            BatchOcrCompletedEvent event = objectMapper.readValue(payload, BatchOcrCompletedEvent.class);
            log.info("Processing OCR completion for batch: {}", event.getBatchId());

            // Update batch status
            Batch batch = batchService.updateBatchStatus(event.getBatchId(), "OCR_COMPLETED");

            // Send real-time notification
            notificationService.notifyBatchStatusUpdate(event.getBatchId(), "OCR_COMPLETED", Map.of(
                    "successfulDocuments", event.getSuccessfulDocuments(),
                    "failedDocuments", event.getFailedDocuments()
            ));

            // Trigger AI analysis if OCR was successful
            if (event.getSuccessfulDocuments() > 0) {
                log.info("Triggering AI analysis for batch: {}", event.getBatchId());
                CompletableFuture.runAsync(() -> {
                    try {
                        analysisService.analyzeBatch(event.getBatchId());
                    } catch (Exception e) {
                        log.error("Failed to analyze batch: {}", event.getBatchId(), e);
                        // Could publish an analysis failed event here
                    }
                });
            }

            // Acknowledge the message
            acknowledgment.acknowledge();
            log.info("Successfully processed BatchOcrCompleted event for batch: {}", event.getBatchId());

        } catch (Exception e) {
            log.error("Error processing BatchOcrCompleted event", e);
            throw new EventProcessingException("Failed to process BatchOcrCompleted event", e);
        }
    }

    /**
     * Handles batch analysis completion events.
     * Updates batch with analysis results and notifies users.
     */
    @KafkaListener(
            topics = "${kafka.topics.batch-analysis-completed:batch-analysis-completed}",
            groupId = "${kafka.consumer.group-id:apex-idp-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public void handleBatchAnalysisCompleted(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received BatchAnalysisCompleted event from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);

        try {
            BatchAnalysisCompletedEvent event = objectMapper.readValue(
                    payload, BatchAnalysisCompletedEvent.class);
            log.info("Processing analysis completion for batch: {}", event.getBatchId());

            // Update batch with analysis results
            batchService.updateBatchAnalysis(
                    event.getBatchId(),
                    event.getSummary(),
                    event.getRecommendations()
            );

            // Update batch status
            batchService.updateBatchStatus(event.getBatchId(), "ANALYSIS_COMPLETED");

            // Send real-time notification with analysis summary
            notificationService.notifyBatchAnalysisComplete(event.getBatchId(), Map.of(
                    "summary", truncateForNotification(event.getSummary(), 200),
                    "hasRecommendations", !event.getRecommendations().isEmpty()
            ));

            // Acknowledge the message
            acknowledgment.acknowledge();
            log.info("Successfully processed BatchAnalysisCompleted event for batch: {}",
                    event.getBatchId());

        } catch (Exception e) {
            log.error("Error processing BatchAnalysisCompleted event", e);
            throw new EventProcessingException("Failed to process BatchAnalysisCompleted event", e);
        }
    }

    /**
     * Handles document processed events.
     * Updates individual document status and extracted data.
     */
    @KafkaListener(
            topics = "${kafka.topics.document-processed:document-processed}",
            groupId = "${kafka.consumer.group-id:apex-idp-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public void handleDocumentProcessed(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.debug("Received DocumentProcessed event from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);

        try {
            DocumentProcessedEvent event = objectMapper.readValue(payload, DocumentProcessedEvent.class);
            log.info("Processing document completion: {} for batch: {}",
                    event.getDocumentId(), event.getBatchId());

            // Update document with extracted data
            documentService.updateDocumentProcessingResult(
                    event.getDocumentId(),
                    event.getStatus(),
                    event.getExtractedData()
            );

            // Send real-time notification for document update
            notificationService.notifyDocumentProcessed(
                    event.getDocumentId(),
                    event.getBatchId(),
                    event.getStatus()
            );

            // Check if all documents in batch are processed
            checkBatchCompletion(event.getBatchId());

            // Acknowledge the message
            acknowledgment.acknowledge();
            log.debug("Successfully processed DocumentProcessed event for document: {}",
                    event.getDocumentId());

        } catch (Exception e) {
            log.error("Error processing DocumentProcessed event", e);
            throw new EventProcessingException("Failed to process DocumentProcessed event", e);
        }
    }

    /**
     * Handles batch status change events.
     * Propagates status updates to connected clients.
     */
    @KafkaListener(
            topics = "${kafka.topics.batch-status-changed:batch-status-changed}",
            groupId = "${kafka.consumer.group-id:apex-idp-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleBatchStatusChanged(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.debug("Received BatchStatusChanged event from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);

        try {
            BatchStatusChangedEvent event = objectMapper.readValue(
                    payload, BatchStatusChangedEvent.class);
            log.info("Batch {} status changed from {} to {}",
                    event.getBatchId(), event.getOldStatus(), event.getNewStatus());

            // Send real-time notification
            notificationService.notifyBatchStatusUpdate(
                    event.getBatchId(),
                    event.getNewStatus(),
                    Map.of("previousStatus", event.getOldStatus())
            );

            // Handle specific status transitions
            handleStatusTransition(event);

            // Acknowledge the message
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing BatchStatusChanged event", e);
            // Non-critical event, log error but don't throw
        }
    }

    /**
     * Handles batch creation events.
     * Initiates OCR processing for newly created batches.
     */
    @KafkaListener(
            topics = "${kafka.topics.batch-created:batch-created}",
            groupId = "${kafka.consumer.group-id:apex-idp-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void handleBatchCreated(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received BatchCreated event from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);

        try {
            BatchCreatedEvent event = objectMapper.readValue(payload, BatchCreatedEvent.class);
            log.info("Processing new batch: {} with {} documents",
                    event.getBatchName(), event.getDocumentCount());

            // Trigger OCR processing for the batch
            CompletableFuture.runAsync(() -> {
                try {
                    batchService.processBatchOcr(event.getBatchId());
                } catch (Exception e) {
                    log.error("Failed to start OCR processing for batch: {}",
                            event.getBatchId(), e);
                    batchService.updateBatchStatus(event.getBatchId(), "OCR_FAILED");
                }
            });

            // Send real-time notification
            notificationService.notifyBatchCreated(event.getBatchId(), Map.of(
                    "batchName", event.getBatchName(),
                    "documentCount", event.getDocumentCount()
            ));

            // Acknowledge the message
            acknowledgment.acknowledge();
            log.info("Successfully initiated processing for batch: {}", event.getBatchId());

        } catch (Exception e) {
            log.error("Error processing BatchCreated event", e);
            throw new EventProcessingException("Failed to process BatchCreated event", e);
        }
    }

    // Helper methods

    /**
     * Checks if all documents in a batch have been processed.
     * Updates batch status accordingly.
     */
    private void checkBatchCompletion(Long batchId) {
        try {
            List<Document> documents = documentService.getDocumentsByBatchId(batchId);
            boolean allProcessed = documents.stream()
                    .allMatch(doc -> doc.getStatus().equals("PROCESSED") ||
                            doc.getStatus().equals("FAILED"));

            if (allProcessed) {
                long successCount = documents.stream()
                        .filter(doc -> doc.getStatus().equals("PROCESSED"))
                        .count();
                long failedCount = documents.size() - successCount;

                log.info("All documents processed for batch {}. Success: {}, Failed: {}",
                        batchId, successCount, failedCount);

                // Update batch status based on results
                String newStatus = failedCount == documents.size() ? "FAILED" : "READY_FOR_ANALYSIS";
                batchService.updateBatchStatus(batchId, newStatus);
            }
        } catch (Exception e) {
            log.error("Error checking batch completion for batch: {}", batchId, e);
        }
    }

    /**
     * Handles specific status transitions that may require additional actions.
     */
    private void handleStatusTransition(BatchStatusChangedEvent event) {
        String newStatus = event.getNewStatus();

        switch (newStatus) {
            case "FAILED":
                log.warn("Batch {} has failed. May need manual intervention.", event.getBatchId());
                // Could send alert notifications here
                break;

            case "COMPLETED":
                log.info("Batch {} processing completed successfully.", event.getBatchId());
                // Could trigger export or integration processes here
                break;

            case "READY_FOR_EXPORT":
                log.info("Batch {} is ready for CPSI export.", event.getBatchId());
                // Could trigger CPSI integration here
                break;

            default:
                log.debug("Batch {} transitioned to status: {}", event.getBatchId(), newStatus);
        }
    }

    /**
     * Truncates text for notification purposes.
     */
    private String truncateForNotification(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Custom exception for event processing failures.
     */
    public static class EventProcessingException extends RuntimeException {
        public EventProcessingException(String message) {
            super(message);
        }

        public EventProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}