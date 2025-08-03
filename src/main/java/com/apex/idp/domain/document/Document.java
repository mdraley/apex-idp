package com.apex.idp.domain.document;

import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.invoice.Invoice;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Document {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "error_message")
    private String errorMessage;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Invoice> invoices = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Document create(Batch batch, String fileName, String filePath) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .batch(batch)
                .fileName(fileName)
                .contentType(determineContentType(fileName))
                .filePath(filePath)
                .status(DocumentStatus.CREATED)
                .retryCount(0)
                .build();
    }

    private static String determineContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) return "application/pdf";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".tiff") || lowerName.endsWith(".tif")) return "image/tiff";
        return "application/octet-stream";
    }

    public void startProcessing() {
        this.status = DocumentStatus.PROCESSING;
    }

    public void completeProcessing() {
        this.status = DocumentStatus.PROCESSED;
    }

    public void failProcessing(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public String getDocumentType() {
        if (contentType != null) {
            if (contentType.contains("pdf")) return "PDF";
            if (contentType.contains("image")) return "IMAGE";
        }
        return "UNKNOWN";
    }

    public boolean isTerminal() {
        return status == DocumentStatus.PROCESSED || status == DocumentStatus.FAILED;
    }

    public enum DocumentStatus {
        CREATED,
        PROCESSING,
        PROCESSED,
        FAILED;

        public boolean isTerminal() {
            return this == PROCESSED || this == FAILED;
        }
    }
}