package com.apex.idp.domain.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, String> {

    Optional<Analysis> findByBatchId(String batchId);

    List<Analysis> findAllByOrderByCreatedAtDesc();
}
