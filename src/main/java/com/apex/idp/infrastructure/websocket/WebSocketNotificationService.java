package com.apex.idp.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;  // FIX: Added missing @Slf4j annotation
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending real-time notifications via WebSocket.
 * Handles batch processing status updates, document events, and error notifications.
 */
@Slf4j  // FIX: Added annotation to generate log field
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Sends batch status update notification.
     */
    public void notifyBatchStatusUpdate(String batchId, String status) {
        log.debug("Sending batch status update - batchId: {}, status: {}", batchId, status);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BATCH_STATUS_UPDATE");
        notification.put("batchId", batchId);
        notification.put("status", status);
        notification.put("timestamp", LocalDateTime.now());

        sendNotification("/topic/batches/" + batchId, notification);
    }

    /**
     * Sends document status update notification.
     */
    public void notifyDocumentStatusUpdate(String documentId, String status) {
        log.debug("Sending document status update - documentId: {}, status: {}",
                documentId, status);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "DOCUMENT_STATUS_UPDATE");
        notification.put("documentId", documentId);
        notification.put("status", status);
        notification.put("timestamp", LocalDateTime.now());

        sendNotification("/topic/documents/" + documentId, notification);
    }

    /**
     * Sends OCR progress notification.
     */
    public void notifyOCRProgress(String batchId, int processed, int total) {
        log.debug("Sending OCR progress - batchId: {}, processed: {}/{}",
                batchId, processed, total);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "OCR_PROGRESS");
        notification.put("batchId", batchId);
        notification.put("processed", processed);
        notification.put("total", total);
        notification.put("percentage", (processed * 100) / total);
        notification.put("timestamp", LocalDateTime.now());

        sendNotification("/topic/batches/" + batchId + "/progress", notification);
    }

    /**
     * Sends analysis completed notification.
     */
    public void notifyAnalysisCompleted(String batchId, String analysisId) {
        log.info("Sending analysis completed notification - batchId: {}, analysisId: {}",
                batchId, analysisId);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "ANALYSIS_COMPLETED");
        notification.put("batchId", batchId);
        notification.put("analysisId", analysisId);
        notification.put("timestamp", LocalDateTime.now());

        sendNotification("/topic/batches/" + batchId + "/analysis", notification);
    }

    /**
     * Sends error notification.
     */
    public void notifyError(String entityId, String entityType, String errorMessage) {
        log.error("Sending error notification - entityType: {}, entityId: {}, error: {}",
                entityType, entityId, errorMessage);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "ERROR");
        notification.put("entityId", entityId);
        notification.put("entityType", entityType);
        notification.put("errorMessage", errorMessage);
        notification.put("timestamp", LocalDateTime.now());

        // Send to entity-specific topic
        String topic = String.format("/topic/%s/%s/error",
                entityType.toLowerCase(), entityId);
        sendNotification(topic, notification);

        // Also send to global error topic
        sendNotification("/topic/errors", notification);
    }

    /**
     * Sends invoice extraction completed notification.
     */
    public void notifyInvoiceExtracted(String documentId, String invoiceId) {
        log.info("Sending invoice extracted notification - documentId: {}, invoiceId: {}",
                documentId, invoiceId);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "INVOICE_EXTRACTED");
        notification.put("documentId", documentId);
        notification.put("invoiceId", invoiceId);
        notification.put("timestamp", LocalDateTime.now());

        sendNotification("/topic/documents/" + documentId + "/invoices", notification);
    }

    /**
     * Sends vendor created notification.
     */
    public void notifyVendorCreated(String vendorId, String vendorName) {
        log.info("Sending vendor created notification - vendorId: {}, vendorName: {}",
                vendorId, vendorName);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "VENDOR_CREATED");
        notification.put("vendorId", vendorId);
        notification.put("vendorName", vendorName);
        notification.put("timestamp", LocalDateTime.now());

        sendNotification("/topic/vendors", notification);
    }

    /**
     * Sends user notification.
     */
    public void notifyUser(String userId, String message, String severity) {
        log.debug("Sending user notification - userId: {}, severity: {}", userId, severity);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "USER_NOTIFICATION");
        notification.put("message", message);
        notification.put("severity", severity);
        notification.put("timestamp", LocalDateTime.now());

        sendNotification("/user/" + userId + "/queue/notifications", notification);
    }

    /**
     * Sends batch processing started notification.
     */
    public void notifyBatchProcessingStarted(String batchId, int documentCount) {
        log.info("Sending batch processing started - batchId: {}, documents: {}",
                batchId, documentCount);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BATCH_PROCESSING_STARTED");
        notification.put("batchId", batchId);
        notification.put("documentCount", documentCount);
        notification.put("timestamp", LocalDateTime.now());

        sendNotification("/topic/batches/" + batchId, notification);
    }

    /**
     * Sends batch processing completed notification.
     */
    public void notifyBatchProcessingCompleted(String batchId, int successCount,
                                               int failureCount) {
        log.info("Batch processing completed - batchId: {}, success: {}, failures: {}",
                batchId, successCount, failureCount);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BATCH_PROCESSING_COMPLETED");
        notification.put("batchId", batchId);
        notification.put("successCount", successCount);
        notification.put("failureCount", failureCount);
        notification.put("timestamp", LocalDateTime.now());

        sendNotification("/topic/batches/" + batchId, notification);
    }

    /**
     * Sends system status notification.
     */
    public void notifySystemStatus(String status, String message) {
        log.info("Sending system status - status: {}, message: {}", status, message);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "SYSTEM_STATUS");
        notification.put("status", status);
        notification.put("message", message);
        notification.put("timestamp", LocalDateTime.now());

        sendNotification("/topic/system/status", notification);
    }

    /**
     * Sends generic notification to specified destination.
     */
    private void sendNotification(String destination, Map<String, Object> notification) {
        try {
            messagingTemplate.convertAndSend(destination, notification);
            log.trace("Notification sent to {}: {}", destination, notification);
        } catch (Exception e) {
            log.error("Failed to send notification to {}: {}", destination, e.getMessage(), e);
        }
    }

    /**
     * Broadcasts notification to all connected clients.
     */
    public void broadcast(Map<String, Object> notification) {
        log.debug("Broadcasting notification: {}", notification.get("type"));
        sendNotification("/topic/broadcast", notification);
    }
}