package com.apex.idp.domain.batch;

import com.apex.idp.domain.analysis.Analysis;
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

@Entity
@Table(name = "batches")
@Getter
@Setter // FIX: Add Setter so services can modify the entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Batch {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    // NEW FIELD
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Document> documents = new ArrayList<>();

    @Column(name = "processed_count")
    private int processedCount;

    // NEW FIELD
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;

    // NEW FIELD
    private String createdBy;

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
                .status(BatchStatus.PENDING)
                .processedCount(0)
                .build();
    }

    public void addDocument(Document document) {
        documents.add(document);
        document.setBatch(this);
    }

    public void startProcessing() {
        if (status != BatchStatus.PENDING) {
            throw new IllegalStateException("Batch must be in PENDING status to start processing");
        }
        this.status = BatchStatus.PROCESSING;
    }

    public void completeProcessing() {
        this.status = BatchStatus.COMPLETED;
    }

    public void failProcessing() {
        this.status = BatchStatus.FAILED;
    }

    public void incrementProcessedCount() {
        this.processedCount++;
        if (processedCount >= documents.size() && status == BatchStatus.PROCESSING) {
            completeProcessing();
        }
    }

    public int getDocumentCount() {
        return documents.size();
    }

    // NEW HELPER METHODS
    public int getProcessedDocumentCount() {
        if (documents == null) return 0;
        return (int) documents.stream().filter(d -> d.getStatus() == DocumentStatus.COMPLETED).count();
    }

    public int getFailedDocumentCount() {
        if (documents == null) return 0;
        return (int) documents.stream().filter(d -> d.getStatus() == DocumentStatus.FAILED).count();
    }

    public int getProcessingProgress() {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        int processedOrFailed = getProcessedDocumentCount() + getFailedDocumentCount();
        return (processedOrFailed * 100) / getDocumentCount();
    }
}
