package com.apex.idp.domain.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Batch entity operations.
 * Provides comprehensive data access methods for batch management.
 */
@Repository
public interface BatchRepository extends JpaRepository<Batch, String>, JpaSpecificationExecutor<Batch> {

    // Status-based queries
    List<Batch> findByStatus(BatchStatus status);

    List<Batch> findByStatusIn(List<BatchStatus> statuses);

    @Query("SELECT COUNT(b) FROM Batch b WHERE b.status = :status")
    long countByStatus(@Param("status") BatchStatus status);

    // User-based queries
    List<Batch> findByCreatedBy(String userId);

    List<Batch> findByProcessedBy(String processedBy);

    // Date-based queries
    List<Batch> findByCreatedAtAfter(LocalDateTime date);

    List<Batch> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Batch> findByUpdatedAtBefore(LocalDateTime cutoffTime);

    // Fetch queries with associations
    @Query("SELECT b FROM Batch b LEFT JOIN FETCH b.documents WHERE b.id = :id")
    Optional<Batch> findByIdWithDocuments(@Param("id") String id);

    @Query("SELECT b FROM Batch b LEFT JOIN FETCH b.documents d WHERE b.status = :status")
    List<Batch> findByStatusWithDocuments(@Param("status") BatchStatus status);

    // Monitoring and maintenance queries
    @Query("SELECT b FROM Batch b WHERE b.status = :processingStatus AND b.updatedAt < :cutoffTime")
    List<Batch> findStuckBatches(@Param("processingStatus") BatchStatus processingStatus, @Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("SELECT b FROM Batch b WHERE b.completedAt IS NULL AND b.status IN :activeStatuses")
    List<Batch> findActiveBatches(@Param("activeStatuses") List<BatchStatus> activeStatuses);

    @Query("SELECT COUNT(b) FROM Batch b WHERE b.createdAt BETWEEN :start AND :end")
    long countBatchesInDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}