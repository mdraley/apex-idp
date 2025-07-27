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

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "file_path")
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(columnDefinition = "TEXT")
    private String ocrText;

    @OneToOne(mappedBy = "document", cascade = CascadeType.ALL)
    private Invoice invoice;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "bucket_name")
    private String bucketName;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "content_type")
    private String contentType;

    // Add getters
    public String getBucketName() {
        return bucketName != null ? bucketName : "apex-documents";
    }

    public String getStoragePath() {
        return storagePath != null ? storagePath : filePath;
    }

    public String getContentType() {
        return contentType != null ? contentType : fileType;
    }

    public static Document create(String fileName, String fileType, String filePath) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .fileName(fileName)
                .fileType(fileType)
                .filePath(filePath)
                .status(DocumentStatus.PENDING)
                .build();
    }

    public void startProcessing() {
        this.status = DocumentStatus.PROCESSING;
    }

    public void completeProcessing(String ocrText) {
        this.status = DocumentStatus.COMPLETED;
        this.ocrText = ocrText;
    }

    public void failProcessing() {
        this.status = DocumentStatus.FAILED;
    }
}
