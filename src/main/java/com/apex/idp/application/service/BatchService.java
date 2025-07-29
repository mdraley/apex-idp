package com.apex.idp.application.service;

import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.batch.BatchRepository;
import com.apex.idp.domain.batch.BatchStatus;
import com.apex.idp.domain.document.Document;
import com.apex.idp.domain.document.DocumentRepository;
import com.apex.idp.infrastructure.kafka.BatchEventProducer;
import com.apex.idp.infrastructure.storage.MinIOStorageService;
import com.apex.idp.infrastructure.storage.StorageService;
import com.apex.idp.infrastructure.websocket.WebSocketNotificationService;
import com.apex.idp.interfaces.dto.BatchDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BatchService {

    private final BatchRepository batchRepository;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final BatchEventProducer batchEventProducer;
    private final WebSocketNotificationService notificationService;

    @Value("${batch.max-file-size:10485760}") // 10MB default
    private long maxFileSize;

    @Value("${batch.allowed-file-types:pdf,jpg,jpeg,png,tiff}")
    private String allowedFileTypes;

    /**
     * Creates a new batch and uploads documents.
     */
    @Transactional
    public BatchDTO createBatch(String name, List<MultipartFile> files) {
        log.info("Creating new batch: {} with {} files", name, files.size());

        // Validate files
        validateFiles(files);

        // Create batch
        Batch batch = Batch.create(generateBatchName(name));
        batch = batchRepository.save(batch);

        // Process and upload files
        for (MultipartFile file : files) {
            Document document = processFile(batch, file);
            batch.addDocument(document);
        }

        // Update batch status
        batch.updateStatus(BatchStatus.PROCESSING);
        batch = batchRepository.save(batch);

        // Send event for async processing
        batchEventProducer.sendBatchCreatedEvent(batch.getId());

        // Send WebSocket notification
        notificationService.notifyBatchStatusUpdate(batch.getId(), batch.getStatus().name());

        log.info("Batch created successfully: {}", batch.getId());
        return convertToDTO(batch);
    }

    /**
     * Retrieves a batch by ID.
     */
    @Transactional(readOnly = true)
    public Optional<BatchDTO> getBatchById(String id) {
        return batchRepository.findByIdWithDocuments(id)
                .map(this::convertToDTO);
    }

    /**
     * Retrieves all batches with pagination.
     */
    @Transactional(readOnly = true)
    public Page<BatchDTO> getAllBatches(int page, int size, String sortBy, String sortDirection) {
        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return batchRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    /**
     * Updates batch status.
     */
    @Transactional
    public void updateBatchStatus(String batchId, BatchStatus status) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        if (!isValidStatusTransition(batch.getStatus(), status)) {
            throw new IllegalStateException(
                    String.format("Invalid status transition from %s to %s",
                            batch.getStatus(), status));
        }

        batch.updateStatus(status);
        batchRepository.save(batch);

        // Send WebSocket notification
        notificationService.notifyBatchStatusUpdate(batchId, status.name());
    }

    /**
     * Deletes a batch and its documents.
     */
    @Transactional
    public void deleteBatch(String batchId) {
        log.info("Deleting batch: {}", batchId);

        Batch batch = batchRepository.findByIdWithDocuments(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        // Delete files from storage
        cleanupBatchFiles(batch);

        // Delete from database
        batchRepository.delete(batch);

        log.info("Batch deleted: {}", batchId);
    }

    /**
     * Gets batch processing statistics.
     */
    @Transactional(readOnly = true)
    public BatchStatistics getBatchStatistics() {
        long totalBatches = batchRepository.count();
        long completedBatches = batchRepository.countByStatus(BatchStatus.COMPLETED);
        long failedBatches = batchRepository.countByStatus(BatchStatus.FAILED);
        long processingBatches = batchRepository.countByStatus(BatchStatus.PROCESSING);

        return new BatchStatistics(totalBatches, completedBatches,
                failedBatches, processingBatches);
    }

    // Private helper methods

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }

        for (MultipartFile file : files) {
            // Check file size
            if (file.getSize() > maxFileSize) {
                throw new IllegalArgumentException(
                        "File too large: " + file.getOriginalFilename());
            }

            // Check file type
            String extension = getFileExtension(file.getOriginalFilename());
            if (!allowedFileTypes.contains(extension.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Invalid file type: " + file.getOriginalFilename());
            }
        }
    }

    private Document processFile(Batch batch, MultipartFile file) {
        try {
            // Store file
            String storagePath = storageService.store(file, batch.getId());

            // Create document
            Document document = Document.create(
                    batch,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    storagePath
            );

            return documentRepository.save(document);

        } catch (Exception e) {
            log.error("Error processing file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to process file", e);
        }
    }

    private BatchDTO convertToDTO(Batch batch) {
        return BatchDTO.builder()
                .id(batch.getId())
                .name(batch.getName())
                .status(batch.getStatus().name())
                .documentCount(batch.getDocumentCount())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .documents(batch.getDocuments().stream()
                        .map(this::convertDocumentToDTO)
                        .collect(Collectors.toList()))
                .build();
    }

    private BatchDTO.DocumentInfo convertDocumentToDTO(Document document) {
        return BatchDTO.DocumentInfo.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .status(document.getStatus().name())
                .build();
    }

    private String generateBatchName(String baseName) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return baseName + "_" + timestamp;
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    private boolean isValidStatusTransition(BatchStatus current, BatchStatus target) {
        // Define valid transitions
        return switch (current) {
            case CREATED -> target == BatchStatus.PROCESSING || target == BatchStatus.FAILED;
            case PROCESSING -> target == BatchStatus.OCR_COMPLETED || target == BatchStatus.FAILED;
            case OCR_COMPLETED -> target == BatchStatus.ANALYSIS_IN_PROGRESS || target == BatchStatus.FAILED;
            case ANALYSIS_IN_PROGRESS -> target == BatchStatus.COMPLETED || target == BatchStatus.FAILED;
            case COMPLETED, FAILED -> false; // Terminal states
        };
    }

    private void cleanupBatchFiles(Batch batch) {
        try {
            for (Document document : batch.getDocuments()) {
                // FIX: Use MinIOStorageService's single-parameter delete method
                if (storageService instanceof MinIOStorageService) {
                    ((MinIOStorageService) storageService).delete(document.getFilePath());
                } else {
                    // Fallback for interface method - assumes default bucket
                    storageService.delete(null, document.getFilePath());
                }
            }
        } catch (Exception e) {
            log.error("Error cleaning up batch files for batch: {}", batch.getId(), e);
        }
    }

    // Inner class for statistics
    public record BatchStatistics(
            long totalBatches,
            long completedBatches,
            long failedBatches,
            long processingBatches
    ) {}
}