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
@Getter
@Setter
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @Column(name = "document_count")
    private int documentCount;

    @Column(name = "processed_count")
    private int processedCount;

    @Column(name = "failed_count")
    private int failedCount;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Document> documents = new ArrayList<>();

    @Column(name = "created_by")
    private String createdBy;

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

    @Column(name = "error_message")
    private String errorMessage;

    /**
     * Static factory method to create a new batch
     */
    public static Batch create(String name, String description, String createdBy) {
        return Batch.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .status(BatchStatus.CREATED)
                .documentCount(0)
                .processedCount(0)
                .failedCount(0)
                .createdBy(createdBy)
                .build();
    }

    /**
     * Adds a document to the batch
     */
    public void addDocument(Document document) {
        documents.add(document);
        document.setBatch(this);
        documentCount++;
    }

    /**
     * Starts batch processing
     */
    public void startProcessing() {
        this.status = BatchStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Marks OCR as completed for the batch
     */
    public void completeOCR() {
        this.status = BatchStatus.OCR_COMPLETED;
        updateProcessingCounts();
    }

    /**
     * Completes analysis for the batch
     */
    public void completeAnalysis() {
        this.status = BatchStatus.ANALYSIS_COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marks the batch as failed
     */
    public void failProcessing(String errorMessage) {
        this.status = BatchStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Fails analysis with error message
     */
    public void failAnalysis(String errorMessage) {
        this.status = BatchStatus.ANALYSIS_FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * Updates processing counts based on document statuses
     */
    public void updateProcessingCounts() {
        this.processedCount = (int) documents.stream()
                .filter(doc -> doc.getStatus() == DocumentStatus.PROCESSED)
                .count();

        this.failedCount = (int) documents.stream()
                .filter(doc -> doc.getStatus() == DocumentStatus.FAILED)
                .count();
    }

    /**
     * Gets the number of processed documents
     */
    public int getProcessedDocumentCount() {
        return (int) documents.stream()
                .filter(doc -> doc.getStatus() == DocumentStatus.PROCESSED)
                .count();
    }

    /**
     * Gets the number of failed documents
     */
    public int getFailedDocumentCount() {
        return (int) documents.stream()
                .filter(doc -> doc.getStatus() == DocumentStatus.FAILED)
                .count();
    }

    /**
     * Checks if all documents are processed
     */
    public boolean areAllDocumentsProcessed() {
        return documents.stream()
                .allMatch(doc -> doc.getStatus().isTerminal());
    }

    /**
     * Gets processing progress as percentage
     */
    public int getProgressPercentage() {
        if (documentCount == 0) return 0;
        return (int) ((processedCount + failedCount) * 100.0 / documentCount);
    }
}

/**
 * Enum representing batch processing statuses
 */
enum BatchStatus {
    CREATED("Created"),
    PROCESSING("Processing"),
    OCR_COMPLETED("OCR Completed"),
    ANALYSIS_IN_PROGRESS("Analysis In Progress"),
    ANALYSIS_COMPLETED("Analysis Completed"),
    ANALYSIS_FAILED("Analysis Failed"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    private final String displayName;

    BatchStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == ANALYSIS_COMPLETED || this == FAILED || this == CANCELLED || this == ANALYSIS_FAILED;
    }
}