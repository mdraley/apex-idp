package com.apex.idp.domain.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, String>, JpaSpecificationExecutor<Batch> {

    List<Batch> findByStatus(BatchStatus status);

    List<Batch> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT b FROM Batch b LEFT JOIN FETCH b.documents WHERE b.id = :id")
    Optional<Batch> findByIdWithDocuments(String id);

    @Query("SELECT COUNT(b) FROM Batch b WHERE b.status = :status")
    long countByStatus(BatchStatus status);
}
