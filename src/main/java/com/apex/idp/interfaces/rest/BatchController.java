package com.apex.idp.interfaces.rest;

import com.apex.idp.application.service.AnalysisService;
import com.apex.idp.application.service.BatchService;
import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.batch.BatchStatus;
import com.apex.idp.interfaces.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for batch document processing operations.
 * Handles batch creation, upload, status tracking, and analysis.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/batches")
@Tag(name = "Batch Processing", description = "Batch document processing APIs")
@Validated
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;
    private final AnalysisService analysisService;

    @Value("${batch.max-file-size:52428800}") // 50MB default
    private long maxFileSize;

    @Value("${batch.max-files:100}")
    private int maxFiles;

    /**
     * Creates a new batch and uploads documents.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create batch", description = "Create a new batch and upload documents")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Batch created successfully",
                    content = @Content(schema = @Schema(implementation = BatchDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "413", description = "Payload too large"),
            @ApiResponse(responseCode = "415", description = "Unsupported media type")
    })
    public ResponseEntity<BatchDTO> createBatch(
            @RequestParam("name")
            @NotEmpty(message = "Batch name is required")
            @Size(max = 255, message = "Batch name must not exceed 255 characters")
            String batchName,

            @RequestParam(value = "description", required = false)
            @Size(max = 1000, message = "Description must not exceed 1000 characters")
            String description,

            @RequestParam("files")
            @NotEmpty(message = "At least one file must be uploaded")
            List<MultipartFile> files,

            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Creating new batch: {} with {} files by user: {}",
                batchName, files.size(), userDetails.getUsername());

        try {
            // Validate file count
            if (files.size() > maxFiles) {
                return ResponseEntity.badRequest()
                        .body(BatchDTO.error("Cannot upload more than " + maxFiles + " files"));
            }

            // Validate each file
            for (MultipartFile file : files) {
                ResponseEntity<BatchDTO> validationError = validateFile(file);
                if (validationError != null) {
                    return validationError;
                }
            }

            // Create batch
            BatchDTO batchDTO = batchService.createBatch(batchName, description, files);

            log.info("Batch created successfully with ID: {} by user: {}",
                    batchDTO.getId(), userDetails.getUsername());

            return ResponseEntity.status(HttpStatus.CREATED).body(batchDTO);

        } catch (IllegalArgumentException e) {
            log.error("Invalid batch creation request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(BatchDTO.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BatchDTO.error("Failed to create batch"));
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

        try {
            Page<Batch> batches = batchService.getBatches(pageable, status, search);
            Page<BatchDTO> batchDTOs = batches.map(this::convertToDTO);

            return ResponseEntity.ok(batchDTOs);

        } catch (Exception e) {
            log.error("Error fetching batches", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
            @PathVariable @Parameter(description = "Batch ID") String id) {

        log.debug("Fetching batch with ID: {}", id);

        Optional<BatchDTO> batchDTO = batchService.getBatchDTOById(id);
        if (batchDTO.isPresent()) {
            return ResponseEntity.ok(batchDTO.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Updates batch status.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update batch status", description = "Update the status of a batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @ApiResponse(responseCode = "404", description = "Batch not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchDTO> updateBatchStatus(
            @PathVariable String id,
            @RequestBody @Valid UpdateStatusRequest request) {

        log.info("Updating batch {} status to {}", id, request.getStatus());

        try {
            BatchStatus newStatus = BatchStatus.valueOf(request.getStatus().toUpperCase());
            Batch updatedBatch = batchService.updateBatchStatus(id, newStatus);
            BatchDTO batchDTO = convertToDTO(updatedBatch);

            return ResponseEntity.ok(batchDTO);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(BatchDTO.error("Invalid status: " + request.getStatus()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(BatchDTO.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating batch status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BatchDTO.error("Failed to update batch status"));
        }
    }

    /**
     * Deletes a batch.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete batch", description = "Delete a batch and its documents")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Batch deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Batch not found"),
            @ApiResponse(responseCode = "409", description = "Batch cannot be deleted in current state")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBatch(@PathVariable String id) {
        log.info("Deleting batch: {}", id);

        try {
            batchService.deleteBatch(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("Error deleting batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets batch analysis results.
     */
    @GetMapping("/{id}/analysis")
    @Operation(summary = "Get batch analysis", description = "Get AI analysis results for a batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analysis retrieved"),
            @ApiResponse(responseCode = "404", description = "Analysis not found")
    })
    public ResponseEntity<AnalysisDTO> getBatchAnalysis(@PathVariable String id) {
        log.debug("Fetching analysis for batch: {}", id);

        Optional<AnalysisDTO> analysis = analysisService.getAnalysisByBatchId(id);
        if (analysis.isPresent()) {
            return ResponseEntity.ok(analysis.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Chats with AI about a batch.
     */
    @PostMapping("/{id}/chat")
    @Operation(summary = "Chat about batch", description = "Chat with AI about batch contents")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Chat response generated"),
            @ApiResponse(responseCode = "404", description = "Batch not found")
    })
    public ResponseEntity<ChatResponseDTO> chatAboutBatch(
            @PathVariable String id,
            @RequestBody @Valid ChatRequestDTO request) {

        log.debug("Chat request for batch: {}", id);

        try {
            ChatResponseDTO response = analysisService.chat(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatResponseDTO.error("Failed to process chat request"));
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
    public ResponseEntity<BatchStatistics> getBatchStatistics() {
        log.debug("Fetching batch statistics");

        try {
            BatchService.BatchStatistics stats = batchService.getBatchStatistics();

            BatchStatistics responseStats = BatchStatistics.builder()
                    .totalBatches(stats.totalBatches())
                    .processingBatches(stats.processingBatches())
                    .completedBatches(stats.completedBatches())
                    .failedBatches(stats.failedBatches())
                    .totalDocuments(1000L) // Would need to be calculated
                    .averageProcessingTime(120L) // seconds
                    .successRate(95.0)
                    .build();

            return ResponseEntity.ok(responseStats);
        } catch (Exception e) {
            log.error("Error fetching batch statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper methods

    private ResponseEntity<BatchDTO> validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BatchDTO.error("Empty file: " + file.getOriginalFilename()));
        }

        if (file.getSize() > maxFileSize) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(BatchDTO.error("File too large: " + file.getOriginalFilename() +
                            " (max: " + (maxFileSize / 1024 / 1024) + "MB)"));
        }

        String contentType = file.getContentType();
        if (!isValidContentType(contentType)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(BatchDTO.error("Unsupported file type: " + contentType));
        }

        return null; // No validation error
    }

    private boolean isValidContentType(String contentType) {
        return contentType != null && (
                contentType.equals("application/pdf") ||
                        contentType.startsWith("image/")
        );
    }

    private BatchDTO convertToDTO(Batch batch) {
        return BatchDTO.builder()
                .id(batch.getId())
                .name(batch.getName())
                .description(batch.getDescription())
                .status(batch.getStatus().toString())
                .documentCount(batch.getDocumentCount())
                .processedCount(batch.getProcessedDocumentCount())
                .failedCount(batch.getFailedDocumentCount())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .documents(batch.getDocuments().stream()
                        .map(this::convertDocumentToDTO)
                        .collect(Collectors.toList()))
                .build();
    }

    private DocumentDTO convertDocumentToDTO(com.apex.idp.domain.document.Document document) {
        return DocumentDTO.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .status(document.getStatus().toString())
                .createdAt(document.getCreatedAt())
                .build();
    }

    // Request/Response DTOs

    public static class UpdateStatusRequest {
        @NotEmpty(message = "Status is required")
        private String status;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchStatistics {
        private Long totalBatches;
        private Long processingBatches;
        private Long completedBatches;
        private Long failedBatches;
        private Long totalDocuments;
        private Long averageProcessingTime; // in seconds
        private Double successRate;
    }
}