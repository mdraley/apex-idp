package com.apex.idp.domain.batch;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class BatchSpecification {

    public static Specification<Batch> findByCriteria(String status, String search) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Add status predicate if status is provided and not empty
            if (StringUtils.hasText(status)) {
                try {
                    BatchStatus batchStatus = BatchStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), batchStatus));
                } catch (IllegalArgumentException e) {
                    // Handle invalid status string if necessary, or ignore
                }
            }

            // Add search predicate if search term is provided
            if (StringUtils.hasText(search)) {
                Predicate namePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + search.toLowerCase() + "%");
                // In a real app, you might search description too
                // Predicate descriptionPredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + search.toLowerCase() + "%");
                predicates.add(namePredicate);
            }

            // Combine predicates with AND
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}