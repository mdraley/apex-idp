package com.apex.idp.domain.batch;

import org.springframework.data.jpa.domain.Specification;

public class BatchSpecification {

    public static Specification<Batch> findByCriteria(BatchStatus status, String search) {
        return Specification.where(hasStatus(status))
                .and(hasSearchTerm(search));
    }

    private static Specification<Batch> hasStatus(BatchStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    private static Specification<Batch> hasSearchTerm(String search) {
        return (root, query, criteriaBuilder) -> {
            if (search == null || search.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            String searchPattern = "%" + search.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), searchPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), searchPattern)
            );
        };
    }
}