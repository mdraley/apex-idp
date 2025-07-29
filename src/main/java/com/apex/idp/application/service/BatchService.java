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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     * This method uses multiple transactions to prevent long-running transactions
     * that might block database resources.
     */
    public BatchDTO createBatch(String name, List<MultipartFile> files) {
        log.info("Creating new batch: {} with {} files", name, files.size());

        // Validate files (outside transaction)
        validateFiles(files);

        // Create initial batch in a short transaction
        Batch batch = createInitialBatch(generateBatchName(name));

        try {
            // Process and upload files (outside transaction)
            List<Document> documents = processFiles(batch, files);

            // Update batch with documents and status in another transaction
            batch = updateBatchWithDocuments(batch.getId(), documents);

            // Send event for async processing (outside transaction)
            batchEventProducer.sendBatchCreatedEvent(batch.getId());

            // Send WebSocket notification (outside transaction)
            notificationService.notifyBatchStatusUpdate(batch.getId(), batch.getStatus().name());

            log.info("Batch created successfully: {}", batch.getId());
            return convertToDTO(batch);
        } catch (Exception e) {
            log.error("Error creating batch: {}", batch.getId(), e);
            // Mark batch as failed in case of error
            try {
                updateBatchStatus(batch.getId(), BatchStatus.FAILED);
            } catch (Exception ex) {
                log.error("Failed to update batch status to FAILED: {}", batch.getId(), ex);
            }
            throw e;
        }
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

        batch.setStatus(status);
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

        // Parse allowed file types as a set for exact matching
        Set<String> allowedExtensions = Arrays.stream(allowedFileTypes.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        for (MultipartFile file : files) {
            // Check file size
            if (file.getSize() > maxFileSize) {
                throw new IllegalArgumentException(
                        "File too large: " + file.getOriginalFilename());
            }

            // Check file type
            String extension = getFileExtension(file.getOriginalFilename());
            if (!allowedExtensions.contains(extension.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Invalid file type: " + file.getOriginalFilename() + ". Allowed types: " + allowedFileTypes);
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
                    storagePath
            );

            return documentRepository.save(document);

        } catch (Exception e) {
            log.error("Error processing file: {}", file.getOriginalFilename(), e);
            // More specific exception handling based on the cause
            if (e instanceof IOException) {
                throw new RuntimeException("I/O error while processing file: " + file.getOriginalFilename(), e);
            } else if (e instanceof IllegalArgumentException) {
                throw new IllegalArgumentException("Invalid file data: " + file.getOriginalFilename(), e);
            } else {
                throw new RuntimeException("Failed to process file: " + file.getOriginalFilename(), e);
            }
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

    private com.apex.idp.interfaces.dto.DocumentDTO convertDocumentToDTO(Document document) {
        return com.apex.idp.interfaces.dto.DocumentDTO.builder()
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
        if (filename == null || filename.isEmpty()) {
            return "";
        }
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
            default -> throw new IllegalArgumentException("Unknown batch status: " + current);
        };
    }

    private void cleanupBatchFiles(Batch batch) {
        try {
            for (Document document : batch.getDocuments()) {
                try {
                    // Use the interface method with null as default bucket
                    storageService.delete(null, document.getFilePath());
                    log.debug("Successfully deleted file: {}", document.getFilePath());
                } catch (StorageService.StorageException e) {
                    log.warn("Could not delete file: {}", document.getFilePath(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error cleaning up batch files for batch: {}", batch.getId(), e);
            // Continue with batch deletion even if file cleanup fails
        }
            }

            /**
             * Creates the initial batch with CREATED status in a separate transaction
             */
            @Transactional
            protected Batch createInitialBatch(String name) {
        Batch batch = Batch.create(name);
        return batchRepository.save(batch);
                }

                /**
                 * Processes all files for a batch outside a transaction
                 */
                private List<Document> processFiles(Batch batch, List<MultipartFile> files) {
        List<Document> documents = new ArrayList<>();
        for (MultipartFile file : files) {
            Document document = processFile(batch, file);
            documents.add(document);
        }
        return documents;
                }

                /**
                 * Updates batch with documents and changes status to PROCESSING in a separate transaction
                 */
                @Transactional
                protected Batch updateBatchWithDocuments(String batchId, List<Document> documents) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        for (Document document : documents) {
            batch.addDocument(document);
        }

        batch.setStatus(BatchStatus.PROCESSING);
        return batchRepository.save(batch);
    }

    // Inner class for statistics
    public record BatchStatistics(
            long totalBatches,
            long completedBatches,
            long failedBatches,
            long processingBatches
    ) {}
}