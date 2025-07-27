package com.apex.idp.infrastructure.websocket;

import com.apex.idp.interfaces.websocket.BatchStatusWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final BatchStatusWebSocketHandler batchStatusHandler;

    public void notifyBatchStatusUpdate(String batchId, String status) {
        Map<String, Object> message = new HashMap<>();
        message.put("batchId", batchId);
        message.put("status", status);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/batch/" + batchId + "/status", message);
        batchStatusHandler.sendBatchUpdate(batchId, message);

        log.info("Sent batch status update for batch: {} with status: {}", batchId, status);
    }

    public void notifyInvoiceCountUpdate(long count) {
        Map<String, Object> message = new HashMap<>();
        message.put("count", count);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/invoices/count", message);

        log.info("Sent invoice count update: {}", count);
    }

    public void notifyVendorCountUpdate(long count) {
        Map<String, Object> message = new HashMap<>();
        message.put("count", count);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/vendors/count", message);

        log.info("Sent vendor count update: {}", count);
    }
}