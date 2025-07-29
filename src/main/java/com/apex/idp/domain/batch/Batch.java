package com.apex.idp.domain.batch;

import com.apex.idp.domain.document.Document;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Batch {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    @Setter
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private BatchStatus status;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Document> documents = new ArrayList<>();

    @Column(name = "processed_count")
    private int processedCount;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Batch create(String name) {
        return Batch.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .status(BatchStatus.CREATED)
                .processedCount(0)
                .build();
    }

    public void addDocument(Document document) {
        documents.add(document);
        document.setBatch(this);
    }

    public void startProcessing() {
        if (status != BatchStatus.CREATED) {
            throw new IllegalStateException("Batch must be in CREATED status to start processing");
        }
        this.status = BatchStatus.PROCESSING;
    }

    public void completeProcessing() {
        this.status = BatchStatus.COMPLETED;
    }

    public void failProcessing() {
        this.status = BatchStatus.FAILED;
    }

    public void completeAnalysis() {
        this.status = BatchStatus.COMPLETED;
    }

    public void failAnalysis(String errorMessage) {
        this.status = BatchStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void incrementProcessedCount() {
        this.processedCount++;
        if (processedCount >= documents.size() && status == BatchStatus.PROCESSING) {
            this.status = BatchStatus.OCR_COMPLETED;
        }
    }

    public int getDocumentCount() {
        return documents.size();
    }

    public int getProcessedDocumentCount() {
        return (int) documents.stream()
                .filter(doc -> doc.getStatus() == Document.DocumentStatus.PROCESSED)
                .count();
    }

    public int getFailedDocumentCount() {
        return (int) documents.stream()
                .filter(doc -> doc.getStatus() == Document.DocumentStatus.FAILED)
                .count();
    }
}