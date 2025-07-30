package com.apex.idp.domain.batch;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications for dynamic batch queries.
 * Provides reusable query predicates for batch filtering.
 */
public class BatchSpecification {

    /**
     * Filter by batch status
     */
    public static Specification<Batch> hasStatus(BatchStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    /**
     * Filter by batch name (case-insensitive partial match)
     */
    public static Specification<Batch> hasNameLike(String name) {
        return (root, query, criteriaBuilder) -> {
            if (name == null || name.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + name.toLowerCase() + "%"
            );
        };
    }

    /**
     * Filter by creation date range
     */
    public static Specification<Batch> createdBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("createdAt"), endDate));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by creator
     */
    public static Specification<Batch> createdBy(String username) {
        return (root, query, criteriaBuilder) -> {
            if (username == null || username.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("createdBy"), username);
        };
    }

    /**
     * Filter batches with errors
     */
    public static Specification<Batch> hasErrors() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.isNotNull(root.get("errorMessage"));
    }

    /**
     * Filter completed batches
     */
    public static Specification<Batch> isCompleted() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.isNotNull(root.get("completedAt"));
    }

    /**
     * Filter by minimum document count
     */
    public static Specification<Batch> hasMinimumDocuments(int minCount) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("documentCount"), minCount);
    }

    /**
     * Complex specification combining multiple criteria
     */
    public static Specification<Batch> searchBatches(BatchSearchCriteria criteria) {
        return Specification.where(hasStatus(criteria.getStatus()))
                .and(hasNameLike(criteria.getName()))
                .and(createdBetween(criteria.getStartDate(), criteria.getEndDate()))
                .and(createdBy(criteria.getCreatedBy()));
    }

    /**
     * Search criteria DTO
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchSearchCriteria {
        private BatchStatus status;
        private String name;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String createdBy;
        private Boolean hasErrors;
        private Integer minDocuments;
    }
}