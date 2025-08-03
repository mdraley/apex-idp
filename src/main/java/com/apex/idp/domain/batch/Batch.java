package com.apex.idp.domain.batch;

import com.apex.idp.domain.document.Document;
import com.apex.idp.domain.document.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Batch entity representing a collection of documents for processing.
 * This is an aggregate root in the domain model.
 */
@Entity
@Table(name = "batches")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Batch {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_type")
    private String sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Document> documents = new ArrayList<>();

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Factory method to create a new batch.
     */
    public static Batch create(String name, String description, String sourceType, String createdBy) {
        return Batch.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .sourceType(sourceType)
                .status(BatchStatus.CREATED)
                .createdBy(createdBy)
                .build();
    }

    /**
     * Adds a document to the batch.
     */
    public void addDocument(Document document) {
        this.documents.add(document);
        document.setBatch(this);
    }

    /**
     * Starts processing the batch.
     */
    public void startProcessing(String processedBy) {
        this.status = BatchStatus.PROCESSING;
        this.processedBy = processedBy;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Marks OCR processing as complete.
     */
    public void completeOCR() {
        this.status = BatchStatus.OCR_COMPLETED;
    }

    /**
     * Marks data extraction as complete.
     */
    public void completeExtraction() {
        this.status = BatchStatus.EXTRACTION_COMPLETED;
    }

    /**
     * Completes analysis for the batch.
     */
    public void completeAnalysis() {
        this.status = BatchStatus.ANALYSIS_COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Completes the batch processing.
     */
    public void complete() {
        this.status = BatchStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Fails the batch processing.
     */
    public void fail(String errorMessage) {
        this.status = BatchStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Gets batch statistics for this batch.
     */
    public BatchStatistics getStatistics() {
        return new BatchStatistics(this.documents);
    }

    /**
     * Extracted class to handle batch statistics calculations.
     */
    public static class BatchStatistics {
        private final List<Document> documents;

        public BatchStatistics(List<Document> documents) {
            this.documents = documents;
        }

        /**
         * Gets the document count in this batch.
         */
        public int getDocumentCount() {
            return this.documents.size();
        }

        /**
         * Gets the number of processed documents.
         */
        public int getProcessedDocumentCount() {
            return (int) documents.stream()
                    .filter(doc -> doc.getStatus() == DocumentStatus.PROCESSED)
                    .count();
        }

        /**
         * Gets the number of failed documents.
         */
        public int getFailedDocumentCount() {
            return (int) documents.stream()
                    .filter(doc -> doc.getStatus() == DocumentStatus.FAILED)
                    .count();
        }

        /**
         * Checks if all documents are processed.
         */
        public boolean areAllDocumentsProcessed() {
            return documents.stream()
                    .allMatch(Document::isTerminal);
        }

        /**
         * Gets processing progress as percentage.
         */
        public int getProgressPercentage() {
            int totalDocuments = getDocumentCount();
            if (totalDocuments == 0) return 0;
            int completedDocuments = getProcessedDocumentCount() + getFailedDocumentCount();
            return (int) (completedDocuments * 100.0 / totalDocuments);
        }
    }
}