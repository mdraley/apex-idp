package com.apex.idp.domain.document;

import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.invoice.Invoice;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Document {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    @Setter
    private Batch batch;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size")
    @Setter
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private DocumentStatus status;

    @Column(columnDefinition = "TEXT")
    private String ocrText;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "error_message")
    private String errorMessage;

    @OneToOne(mappedBy = "document", cascade = CascadeType.ALL)
    @Setter
    private Invoice invoice;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Document create(String fileName, String contentType, String filePath) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .fileName(fileName)
                .contentType(contentType)
                .filePath(filePath)
                .status(DocumentStatus.CREATED)
                .retryCount(0)
                .build();
    }

    public void startProcessing() {
        this.status = DocumentStatus.PROCESSING;
    }

    public void completeProcessing(String ocrText) {
        this.status = DocumentStatus.PROCESSED;
        this.ocrText = ocrText;
    }

    public void failProcessing(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public String getDocumentType() {
        // Simple mapping based on content type
        if (contentType != null) {
            if (contentType.contains("pdf")) return "PDF";
            if (contentType.contains("image")) return "IMAGE";
        }
        return "UNKNOWN";
    }

    public enum DocumentStatus {
        CREATED,
        PROCESSING,
        PROCESSED,
        FAILED
    }
}