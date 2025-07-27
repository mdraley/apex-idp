package com.apex.idp.interfaces.rest;

import com.apex.idp.application.service.BatchService;
import com.apex.idp.application.service.ChatService;
import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.batch.BatchStatus;
import com.apex.idp.interfaces.dto.BatchDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.*;

/**
 * REST controller for batch document processing operations.
 * Handles batch creation, upload, status tracking, and analysis.
 */
@RestController
@RequestMapping("/api/v1/batches")
@Tag(name = "Batch Processing", description = "Batch document processing APIs")
@Validated
@PreAuthorize("isAuthenticated()")
public class BatchController {

    private static final Logger log = LoggerFactory.getLogger(BatchController.class);

    private final BatchService batchService;
    private final ChatService chatService;

    public BatchController(BatchService batchService, ChatService chatService) {
        this.batchService = batchService;
        this.chatService = chatService;
    }

    /**
     * Creates a new batch and uploads documents.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create batch", description = "Create a new batch and upload documents")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Batch created successfully",
                    content = @Content(schema = @Schema(implementation = BatchDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "413", description = "Payload too large")
    })
    public ResponseEntity<BatchDTO> createBatch(
            @RequestParam("name") @NotEmpty String batchName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("files") @NotEmpty List<MultipartFile> files) {

        log.info("Creating new batch: {} with {} files", batchName, files.size());

        try {
            // Validate files
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest().build();
                }
            }

            // Create batch
            Batch batch = batchService.createBatch(batchName, description, files);
            BatchDTO batchDTO = convertToDTO(batch);

            log.info("Batch created successfully with ID: {}", batch.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(batchDTO);

        } catch (IllegalArgumentException e) {
            log.error("Invalid batch creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets all batches with pagination.
     */
    @GetMapping
    @Operation(summary = "List batches", description = "Get paginated list of batches")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Batches retrieved successfully")
    })
    public ResponseEntity<Page<BatchDTO>> getBatches(
            @PageableDefault(size = 20, sort = "createdAt,desc") Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        log.debug("Fetching batches - page: {}, size: {}, status: {}, search: {}",
                pageable.getPageNumber(), pageable.getPageSize(), status, search);

        Page<Batch> batches = batchService.getBatches(pageable, status, search);
        Page<BatchDTO> batchDTOs = batches.map(this::convertToDTO);

        return ResponseEntity.ok(batchDTOs);
    }

    /**
     * Gets a specific batch by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get batch", description = "Get batch details by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Batch found"),
            @ApiResponse(responseCode = "404", description = "Batch not found")
    })
    public ResponseEntity<BatchDTO> getBatch(
            @PathVariable @Parameter(description = "Batch ID") String  id) {

        log.debug("Fetching batch with ID: {}", id);

        Optional<Batch> batch = batchService.getBatchById(id);
        if (batch.isPresent()) {
            BatchDTO batchDTO = convertToDTO(batch.get());
            return ResponseEntity.ok(batchDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Updates batch information.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update batch", description = "Update batch information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Batch updated successfully"),
            @ApiResponse(responseCode = "404", description = "Batch not found")
    })
    public ResponseEntity<BatchDTO> updateBatch(
            @PathVariable String id,
            @Valid @RequestBody UpdateBatchRequest request) {

        log.info("Updating batch ID: {}", id);

        try {
            Batch updatedBatch = batchService.updateBatch(id, request.getName(),
                    request.getDescription());
            BatchDTO batchDTO = convertToDTO(updatedBatch);
            return ResponseEntity.ok(batchDTO);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deletes a batch.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete batch", description = "Delete a batch and its documents")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Batch deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Batch not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBatch(@PathVariable String id) {
        log.info("Deleting batch ID: {}", id);

        try {
            batchService.deleteBatch(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets batch processing status.
     */
    @GetMapping("/{id}/status")
    @Operation(summary = "Get batch status", description = "Get current processing status of a batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved"),
            @ApiResponse(responseCode = "404", description = "Batch not found")
    })
    public ResponseEntity<BatchStatusResponse> getBatchStatus(@PathVariable String id) {
        log.debug("Getting status for batch ID: {}", id);

        Optional<Batch> batch = batchService.getBatchById(id);
        if (batch.isPresent()) {
            BatchStatusResponse status = new BatchStatusResponse(
                    batch.get().getId(),
                    batch.get().getStatus(),
                    batch.get().getProcessingProgress(),
                    batch.get().getDocuments().size(),
                    batch.get().getProcessedDocumentCount(),
                    batch.get().getFailedDocumentCount()
            );
            return ResponseEntity.ok(status);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Gets batch analysis results.
     */
    @GetMapping("/{id}/analysis")
    @Operation(summary = "Get batch analysis", description = "Get AI analysis results for a batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analysis retrieved"),
            @ApiResponse(responseCode = "404", description = "Batch or analysis not found")
    })
    public ResponseEntity<BatchAnalysisResponse> getBatchAnalysis(@PathVariable String id) {
        log.debug("Getting analysis for batch ID: {}", id);

        Optional<Batch> batch = batchService.getBatchById(id);
        if (batch.isPresent() && batch.get().getAnalysis() != null) {
            BatchAnalysisResponse analysis = new BatchAnalysisResponse(
                    batch.get().getId(),
                    batch.get().getAnalysis().getSummary(),
                    batch.get().getAnalysis().getRecommendations(),
                    batch.get().getAnalysis().getCreatedAt()
            );
            return ResponseEntity.ok(analysis);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Reprocesses a failed batch.
     */
    @PostMapping("/{id}/reprocess")
    @Operation(summary = "Reprocess batch", description = "Reprocess a failed or partially failed batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Reprocessing started"),
            @ApiResponse(responseCode = "404", description = "Batch not found"),
            @ApiResponse(responseCode = "409", description = "Batch cannot be reprocessed")
    })
    public ResponseEntity<Map<String, String>> reprocessBatch(@PathVariable String id) {
        log.info("Reprocessing batch ID: {}", id);

        try {
            batchService.reprocessBatch(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Batch reprocessing started");
            response.put("batchId", id.toString());
            return ResponseEntity.accepted().body(response);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        } catch (Exception e) {
            log.error("Error reprocessing batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Chats with AI about a batch.
     */
    @PostMapping("/{id}/chat")
    @Operation(summary = "Chat about batch", description = "Send a message to AI about this batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Chat response received"),
            @ApiResponse(responseCode = "404", description = "Batch not found")
    })
    public ResponseEntity<ChatResponse> chatAboutBatch(
            @PathVariable String id,
            @Valid @RequestBody ChatRequest request) {

        log.debug("Chat request for batch ID: {} - {}", id,
                request.getMessage().substring(0, Math.min(50, request.getMessage().length())));

        try {
            String response = chatService.chatAboutBatch(id, request.getMessage(),
                    request.getConversationId());

            ChatResponse chatResponse = new ChatResponse(
                    response,
                    request.getConversationId() != null ? request.getConversationId() : UUID.randomUUID().toString(),
                    LocalDateTime.now()
            );

            return ResponseEntity.ok(chatResponse);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets batch statistics.
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get batch statistics", description = "Get overall batch processing statistics")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved")
    })
    public ResponseEntity<BatchStatistics> getBatchStatistics(
            @RequestParam(required = false) String period) {

        BatchStatistics stats = batchService.getBatchStatistics(period);
        return ResponseEntity.ok(stats);
    }

    // Helper method to convert Batch to BatchDTO
    private BatchDTO convertToDTO(Batch batch) {
        return new BatchDTO(
                batch.getId(),
                batch.getName(),
                batch.getDescription(),
                batch.getStatus(),
                batch.getDocuments().size(),
                batch.getProcessedDocumentCount(),
                batch.getFailedDocumentCount(),
                batch.getProcessingProgress(),
                batch.getAnalysis() != null ? batch.getAnalysis().getSummary() : null,
                batch.getAnalysis() != null ? batch.getAnalysis().getRecommendations() : null,
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                batch.getCreatedBy()
        );
    }

    // Request/Response DTOs

    public static class UpdateBatchRequest {
        @NotEmpty
        private String name;
        private String description;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class BatchStatusResponse {
        private final Long batchId;
        private final String status;
        private final Integer progress;
        private final Integer totalDocuments;
        private final Integer processedDocuments;
        private final Integer failedDocuments;

        public BatchStatusResponse(Long batchId, String status, Integer progress,
                                   Integer totalDocuments, Integer processedDocuments,
                                   Integer failedDocuments) {
            this.batchId = batchId;
            this.status = status;
            this.progress = progress;
            this.totalDocuments = totalDocuments;
            this.processedDocuments = processedDocuments;
            this.failedDocuments = failedDocuments;
        }

        // Getters
        public Long getBatchId() { return batchId; }
        public String getStatus() { return status; }
        public Integer getProgress() { return progress; }
        public Integer getTotalDocuments() { return totalDocuments; }
        public Integer getProcessedDocuments() { return processedDocuments; }
        public Integer getFailedDocuments() { return failedDocuments; }
    }

    public static class BatchAnalysisResponse {
        private final Long batchId;
        private final String summary;
        private final String recommendations;
        private final LocalDateTime analyzedAt;

        public BatchAnalysisResponse(Long batchId, String summary, String recommendations,
                                     LocalDateTime analyzedAt) {
            this.batchId = batchId;
            this.summary = summary;
            this.recommendations = recommendations;
            this.analyzedAt = analyzedAt;
        }

        // Getters
        public Long getBatchId() { return batchId; }
        public String getSummary() { return summary; }
        public String getRecommendations() { return recommendations; }
        public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    }

    public static class ChatRequest {
        @NotEmpty
        private String message;
        private String conversationId;

        // Getters and setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }
    }

    public static class ChatResponse {
        private final String response;
        private final String conversationId;
        private final LocalDateTime timestamp;

        public ChatResponse(String response, String conversationId, LocalDateTime timestamp) {
            this.response = response;
            this.conversationId = conversationId;
            this.timestamp = timestamp;
        }

        // Getters
        public String getResponse() { return response; }
        public String getConversationId() { return conversationId; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class BatchStatistics {
        private final Long totalBatches;
        private final Long completedBatches;
        private final Long failedBatches;
        private final Long processingBatches;
        private final Long totalDocuments;
        private final Long processedDocuments;
        private final Double averageProcessingTime;
        private final Map<String, Long> batchesByStatus;

        public BatchStatistics(Long totalBatches, Long completedBatches, Long failedBatches,
                               Long processingBatches, Long totalDocuments, Long processedDocuments,
                               Double averageProcessingTime, Map<String, Long> batchesByStatus) {
            this.totalBatches = totalBatches;
            this.completedBatches = completedBatches;
            this.failedBatches = failedBatches;
            this.processingBatches = processingBatches;
            this.totalDocuments = totalDocuments;
            this.processedDocuments = processedDocuments;
            this.averageProcessingTime = averageProcessingTime;
            this.batchesByStatus = batchesByStatus;
        }

        // Getters
        public Long getTotalBatches() { return totalBatches; }
        public Long getCompletedBatches() { return completedBatches; }
        public Long getFailedBatches() { return failedBatches; }
        public Long getProcessingBatches() { return processingBatches; }
        public Long getTotalDocuments() { return totalDocuments; }
        public Long getProcessedDocuments() { return processedDocuments; }
        public Double getAverageProcessingTime() { return averageProcessingTime; }
        public Map<String, Long> getBatchesByStatus() { return batchesByStatus; }
    }
}