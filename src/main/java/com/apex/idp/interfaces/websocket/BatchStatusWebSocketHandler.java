package com.apex.idp.interfaces.websocket;

import com.apex.idp.application.service.BatchService;
import com.apex.idp.domain.batch.Batch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time batch status updates.
 * Provides live updates on batch processing progress and notifications.
 */
@Controller
@EnableWebSocketMessageBroker
public class BatchStatusWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BatchStatusWebSocketHandler.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final BatchService batchService;
    private final ObjectMapper objectMapper;

    // Track active subscriptions
    private final Map<String, Set<Long>> userBatchSubscriptions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> batchUserSubscriptions = new ConcurrentHashMap<>();

    public BatchStatusWebSocketHandler(SimpMessagingTemplate messagingTemplate,
                                       BatchService batchService,
                                       ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.batchService = batchService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles subscription to batch status updates.
     */
    @SubscribeMapping("/batch/{batchId}/status")
    public BatchStatusUpdate subscribeToBatchStatus(@DestinationVariable Long batchId,
                                                    Principal principal) {
        log.debug("User {} subscribed to batch {} status", principal.getName(), batchId);

        // Track subscription
        trackSubscription(principal.getName(), batchId);

        // Return current status
        Optional<Batch> batch = batchService.getBatchById(batchId);
        if (batch.isPresent()) {
            return createStatusUpdate(batch.get());
        } else {
            return new BatchStatusUpdate(batchId, "NOT_FOUND", 0, null);
        }
    }

    /**
     * Handles unsubscription from batch status updates.
     */
    @MessageMapping("/batch/{batchId}/unsubscribe")
    public void unsubscribeFromBatchStatus(@DestinationVariable Long batchId,
                                           Principal principal) {
        log.debug("User {} unsubscribed from batch {} status", principal.getName(), batchId);
        untrackSubscription(principal.getName(), batchId);
    }

    /**
     * Subscribes to all user's batches status updates.
     */
    @SubscribeMapping("/user/batches/status")
    public UserBatchesStatus subscribeToUserBatches(Principal principal) {
        log.debug("User {} subscribed to all their batches status", principal.getName());

        List<BatchStatusUpdate> batchStatuses = batchService.getUserBatches(principal.getName())
                .stream()
                .map(this::createStatusUpdate)
                .toList();

        return new UserBatchesStatus(principal.getName(), batchStatuses);
    }

    /**
     * Handles chat messages about a batch.
     */
    @MessageMapping("/batch/{batchId}/chat")
    @SendTo("/topic/batch/{batchId}/chat")
    public ChatMessage handleBatchChat(@DestinationVariable Long batchId,
                                       @Payload ChatMessage message,
                                       Principal principal) {
        log.debug("Chat message for batch {} from user {}", batchId, principal.getName());

        message.setUserId(principal.getName());
        message.setTimestamp(LocalDateTime.now());

        // Process chat message if needed (e.g., save to database)
        // ...

        return message;
    }

    /**
     * Sends batch status update to all subscribed users.
     */
    public void sendBatchStatusUpdate(Long batchId, String status, Integer progress,
                                      Map<String, Object> additionalData) {
        log.debug("Sending batch {} status update: {} ({}%)", batchId, status, progress);

        BatchStatusUpdate update = new BatchStatusUpdate(batchId, status, progress, additionalData);

        // Send to all users subscribed to this batch
        Set<String> subscribedUsers = batchUserSubscriptions.get(batchId);
        if (subscribedUsers != null) {
            for (String userId : subscribedUsers) {
                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/batch/" + batchId + "/status",
                        update
                );
            }
        }

        // Also send to the general batch topic
        messagingTemplate.convertAndSend("/topic/batch/" + batchId + "/status", update);
    }

    /**
     * Sends document processing update.
     */
    public void sendDocumentProcessedUpdate(Long documentId, Long batchId, String status) {
        log.debug("Sending document {} processed update for batch {}", documentId, batchId);

        DocumentProcessedUpdate update = new DocumentProcessedUpdate(
                documentId, batchId, status, LocalDateTime.now()
        );

        messagingTemplate.convertAndSend("/topic/batch/" + batchId + "/document", update);
    }

    /**
     * Sends batch analysis complete notification.
     */
    public void sendBatchAnalysisComplete(Long batchId, String summary) {
        log.debug("Sending batch {} analysis complete notification", batchId);

        AnalysisCompleteNotification notification = new AnalysisCompleteNotification(
                batchId,
                "Analysis Complete",
                summary,
                LocalDateTime.now()
        );

        // Send to all subscribed users
        Set<String> subscribedUsers = batchUserSubscriptions.get(batchId);
        if (subscribedUsers != null) {
            for (String userId : subscribedUsers) {
                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/notifications",
                        notification
                );
            }
        }
    }

    /**
     * Broadcasts system-wide notifications.
     */
    @MessageMapping("/broadcast")
    @SendTo("/topic/broadcast")
    public SystemNotification broadcastNotification(@Payload SystemNotification notification,
                                                    Principal principal) {
        // Only allow admins to broadcast
        if (isAdmin(principal)) {
            notification.setTimestamp(LocalDateTime.now());
            return notification;
        }
        return null;
    }

    /**
     * Periodic heartbeat to keep connections alive.
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void sendHeartbeat() {
        HeartbeatMessage heartbeat = new HeartbeatMessage(
                LocalDateTime.now(),
                "ALIVE"
        );
        messagingTemplate.convertAndSend("/topic/heartbeat", heartbeat);
    }

    /**
     * Sends real-time dashboard statistics update.
     */
    public void sendDashboardUpdate(DashboardStats stats) {
        log.debug("Sending dashboard statistics update");
        messagingTemplate.convertAndSend("/topic/dashboard/stats", stats);
    }

    // Helper methods

    private void trackSubscription(String userId, Long batchId) {
        userBatchSubscriptions.computeIfAbsent(userId, k -> new HashSet<>()).add(batchId);
        batchUserSubscriptions.computeIfAbsent(batchId, k -> new HashSet<>()).add(userId);
    }

    private void untrackSubscription(String userId, Long batchId) {
        Set<Long> userBatches = userBatchSubscriptions.get(userId);
        if (userBatches != null) {
            userBatches.remove(batchId);
            if (userBatches.isEmpty()) {
                userBatchSubscriptions.remove(userId);
            }
        }

        Set<String> batchUsers = batchUserSubscriptions.get(batchId);
        if (batchUsers != null) {
            batchUsers.remove(userId);
            if (batchUsers.isEmpty()) {
                batchUserSubscriptions.remove(batchId);
            }
        }
    }

    private BatchStatusUpdate createStatusUpdate(Batch batch) {
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("name", batch.getName());
        additionalData.put("documentCount", batch.getDocuments().size());
        additionalData.put("processedCount", batch.getProcessedDocumentCount());
        additionalData.put("failedCount", batch.getFailedDocumentCount());

        return new BatchStatusUpdate(
                batch.getId(),
                batch.getStatus(),
                batch.getProcessingProgress(),
                additionalData
        );
    }

    private boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication) {
            Authentication auth = (Authentication) principal;
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }

    // Message DTOs

    public static class BatchStatusUpdate {
        private final Long batchId;
        private final String status;
        private final Integer progress;
        private final Map<String, Object> data;
        private final LocalDateTime timestamp;

        public BatchStatusUpdate(Long batchId, String status, Integer progress,
                                 Map<String, Object> data) {
            this.batchId = batchId;
            this.status = status;
            this.progress = progress;
            this.data = data;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public Long getBatchId() { return batchId; }
        public String getStatus() { return status; }
        public Integer getProgress() { return progress; }
        public Map<String, Object> getData() { return data; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class UserBatchesStatus {
        private final String userId;
        private final List<BatchStatusUpdate> batches;
        private final LocalDateTime timestamp;

        public UserBatchesStatus(String userId, List<BatchStatusUpdate> batches) {
            this.userId = userId;
            this.batches = batches;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getUserId() { return userId; }
        public List<BatchStatusUpdate> getBatches() { return batches; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class DocumentProcessedUpdate {
        private final Long documentId;
        private final Long batchId;
        private final String status;
        private final LocalDateTime timestamp;

        public DocumentProcessedUpdate(Long documentId, Long batchId, String status,
                                       LocalDateTime timestamp) {
            this.documentId = documentId;
            this.batchId = batchId;
            this.status = status;
            this.timestamp = timestamp;
        }

        // Getters
        public Long getDocumentId() { return documentId; }
        public Long getBatchId() { return batchId; }
        public String getStatus() { return status; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class ChatMessage {
        private String userId;
        private String message;
        private String conversationId;
        private LocalDateTime timestamp;

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class AnalysisCompleteNotification {
        private final Long batchId;
        private final String title;
        private final String message;
        private final LocalDateTime timestamp;

        public AnalysisCompleteNotification(Long batchId, String title, String message,
                                            LocalDateTime timestamp) {
            this.batchId = batchId;
            this.title = title;
            this.message = message;
            this.timestamp = timestamp;
        }

        // Getters
        public Long getBatchId() { return batchId; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class SystemNotification {
        private String type; // "info", "warning", "error"
        private String message;
        private LocalDateTime timestamp;

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class HeartbeatMessage {
        private final LocalDateTime timestamp;
        private final String status;

        public HeartbeatMessage(LocalDateTime timestamp, String status) {
            this.timestamp = timestamp;
            this.status = status;
        }

        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getStatus() { return status; }
    }

    public static class DashboardStats {
        private final Long totalBatches;
        private final Long totalInvoices;
        private final Long totalVendors;
        private final Long processingBatches;
        private final Map<String, Object> additionalStats;

        public DashboardStats(Long totalBatches, Long totalInvoices, Long totalVendors,
                              Long processingBatches, Map<String, Object> additionalStats) {
            this.totalBatches = totalBatches;
            this.totalInvoices = totalInvoices;
            this.totalVendors = totalVendors;
            this.processingBatches = processingBatches;
            this.additionalStats = additionalStats;
        }

        // Getters
        public Long getTotalBatches() { return totalBatches; }
        public Long getTotalInvoices() { return totalInvoices; }
        public Long getTotalVendors() { return totalVendors; }
        public Long getProcessingBatches() { return processingBatches; }
        public Map<String, Object> getAdditionalStats() { return additionalStats; }
    }
}