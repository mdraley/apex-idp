package com.apex.idp.interfaces.websocket;

import com.apex.idp.application.service.BatchService;
import com.apex.idp.domain.batch.Batch;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket controller for real-time batch status updates.
 * Provides live updates on batch processing progress and notifications.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class BatchStatusWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final BatchService batchService;
    private final ObjectMapper objectMapper;

    // Track active subscriptions
    private final Map<String, Set<String>> userBatchSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> batchUserSubscriptions = new ConcurrentHashMap<>();

    /**
     * Handles subscription to batch status updates.
     */
    @SubscribeMapping("/batch/{batchId}/status")
    public BatchStatusUpdate subscribeToBatchStatus(@DestinationVariable String batchId,
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
    public void unsubscribeFromBatchStatus(@DestinationVariable String batchId,
                                           Principal principal) {
        log.debug("User {} unsubscribed from batch {} status", principal.getName(), batchId);
        untrackSubscription(principal.getName(), batchId);
    }

    /**
     * Subscribes to all user's batches status updates.
     */
    @SubscribeMapping("/user/batches/status")
    @SendToUser("/queue/batches/status")
    public UserBatchesStatus subscribeToUserBatches(Principal principal) {
        log.debug("User {} subscribed to all their batches status", principal.getName());

        List<BatchStatusUpdate> batchStatuses = batchService.getUserBatches(principal.getName())
                .stream()
                .map(this::createStatusUpdate)
                .collect(Collectors.toList());

        return new UserBatchesStatus(principal.getName(), batchStatuses);
    }

    /**
     * Handles chat messages about a batch.
     */
    @MessageMapping("/batch/{batchId}/chat")
    @SendTo("/topic/batch/{batchId}/chat")
    public ChatMessage handleBatchChat(@DestinationVariable String batchId,
                                       @Payload ChatMessage message,
                                       Principal principal) {
        log.debug("Chat message for batch {} from user {}", batchId, principal.getName());

        message.setUserId(principal.getName());
        message.setTimestamp(LocalDateTime.now());

        return message;
    }

    /**
     * Sends batch update to specific batch subscribers.
     */
    public void sendBatchUpdate(String batchId, Map<String, Object> update) {
        try {
            String destination = "/topic/batch/" + batchId + "/status";
            messagingTemplate.convertAndSend(destination, update);

            log.debug("Sent batch update to subscribers of batch: {}", batchId);

        } catch (Exception e) {
            log.error("Failed to send batch update for batch: {}", batchId, e);
        }
    }

    /**
     * Sends personalized updates to specific users.
     */
    public void sendUserBatchUpdate(String userId, String batchId, BatchStatusUpdate update) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/batch/" + batchId + "/status",
                    update
            );

            log.debug("Sent batch update to user: {} for batch: {}", userId, batchId);

        } catch (Exception e) {
            log.error("Failed to send user batch update", e);
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
            log.info("Broadcasting system notification from user: {}", principal.getName());
            return notification;
        }
        return null;
    }

    /**
     * Periodic heartbeat to keep connections alive.
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void sendHeartbeat() {
        try {
            HeartbeatMessage heartbeat = new HeartbeatMessage(
                    LocalDateTime.now(),
                    "ALIVE",
                    getConnectionStats()
            );
            messagingTemplate.convertAndSend("/topic/heartbeat", heartbeat);

        } catch (Exception e) {
            log.error("Failed to send heartbeat", e);
        }
    }

    /**
     * Sends real-time dashboard statistics update.
     */
    public void sendDashboardUpdate(DashboardStats stats) {
        try {
            log.debug("Sending dashboard statistics update");
            messagingTemplate.convertAndSend("/topic/dashboard/stats", stats);

        } catch (Exception e) {
            log.error("Failed to send dashboard update", e);
        }
    }

    // Helper methods

    private void trackSubscription(String userId, String batchId) {
        userBatchSubscriptions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(batchId);
        batchUserSubscriptions.computeIfAbsent(batchId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    private void untrackSubscription(String userId, String batchId) {
        Set<String> userBatches = userBatchSubscriptions.get(userId);
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
        additionalData.put("description", batch.getDescription());
        additionalData.put("documentCount", batch.getDocuments().size());
        additionalData.put("processedCount", batch.getProcessedDocumentCount());
        additionalData.put("failedCount", batch.getFailedDocumentCount());
        additionalData.put("createdAt", batch.getCreatedAt());

        int progress = calculateProgress(batch);

        return new BatchStatusUpdate(
                batch.getId(),
                batch.getStatus().toString(),
                progress,
                additionalData
        );
    }

    private int calculateProgress(Batch batch) {
        if (batch.getDocuments().isEmpty()) return 0;

        int processed = batch.getProcessedDocumentCount();
        int total = batch.getDocuments().size();

        return Math.round((processed * 100.0f) / total);
    }

    private boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication) {
            Authentication auth = (Authentication) principal;
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }

    private Map<String, Object> getConnectionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeUsers", userBatchSubscriptions.size());
        stats.put("activeBatches", batchUserSubscriptions.size());
        stats.put("totalSubscriptions",
                userBatchSubscriptions.values().stream().mapToInt(Set::size).sum());
        return stats;
    }

    // Message DTOs

    public static class BatchStatusUpdate {
        private final String batchId;
        private final String status;
        private final Integer progress;
        private final Map<String, Object> data;
        private final LocalDateTime timestamp;

        public BatchStatusUpdate(String batchId, String status, Integer progress,
                                 Map<String, Object> data) {
            this.batchId = batchId;
            this.status = status;
            this.progress = progress;
            this.data = data;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getBatchId() { return batchId; }
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

    public static class SystemNotification {
        private String type;
        private String title;
        private String message;
        private String severity;
        private LocalDateTime timestamp;

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class HeartbeatMessage {
        private final LocalDateTime timestamp;
        private final String status;
        private final Map<String, Object> stats;

        public HeartbeatMessage(LocalDateTime timestamp, String status, Map<String, Object> stats) {
            this.timestamp = timestamp;
            this.status = status;
            this.stats = stats;
        }

        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getStatus() { return status; }
        public Map<String, Object> getStats() { return stats; }
    }

    public static class DashboardStats {
        private final Long totalBatches;
        private final Long activeBatches;
        private final Long totalInvoices;
        private final Long totalVendors;
        private final Map<String, Long> batchesByStatus;
        private final LocalDateTime timestamp;

        public DashboardStats(Long totalBatches, Long activeBatches, Long totalInvoices,
                              Long totalVendors, Map<String, Long> batchesByStatus) {
            this.totalBatches = totalBatches;
            this.activeBatches = activeBatches;
            this.totalInvoices = totalInvoices;
            this.totalVendors = totalVendors;
            this.batchesByStatus = batchesByStatus;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public Long getTotalBatches() { return totalBatches; }
        public Long getActiveBatches() { return activeBatches; }
        public Long getTotalInvoices() { return totalInvoices; }
        public Long getTotalVendors() { return totalVendors; }
        public Map<String, Long> getBatchesByStatus() { return batchesByStatus; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}