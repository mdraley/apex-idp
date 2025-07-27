package com.apex.idp.domain.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {

    List<Document> findByBatchId(String batchId);

    List<Document> findByStatus(DocumentStatus status);

    List<Document> findByBatchIdAndStatus(String batchId, DocumentStatus status);
}
