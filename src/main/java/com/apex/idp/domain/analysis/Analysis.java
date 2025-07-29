package com.apex.idp.domain.analysis;

import com.apex.idp.domain.batch.Batch;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Analysis {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @ElementCollection
    @CollectionTable(name = "analysis_recommendations", joinColumns = @JoinColumn(name = "analysis_id"))
    @Column(name = "recommendation")
    @Builder.Default
    private List<String> recommendations = List.of();

    @ElementCollection
    @CollectionTable(name = "analysis_metadata", joinColumns = @JoinColumn(name = "analysis_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Analysis create(Batch batch, String summary, String recommendations) {
        return Analysis.builder()
                .id(UUID.randomUUID().toString())
                .batch(batch)
                .summary(summary)
                .recommendations(parseRecommendations(recommendations))
                .build();
    }

    private static List<String> parseRecommendations(String recommendations) {
        if (recommendations == null || recommendations.trim().isEmpty()) {
            return List.of();
        }
        // Simple parsing - could be improved based on actual format
        return List.of(recommendations.split("\n"));
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata.clear();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (value != null) {
                    this.metadata.put(key, value.toString());
                }
            });
        }
    }
}