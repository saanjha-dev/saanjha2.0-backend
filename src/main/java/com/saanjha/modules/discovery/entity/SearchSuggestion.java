package com.saanjha.modules.discovery.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Autocomplete source, incrementally maintained as documents are projected. */
@Entity
@Table(name = "dsc_search_suggestions", schema = "dsc", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"term", "entity_type"})
})
@Getter
@Setter
public class SearchSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String term;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private SuggestionEntityType entityType;

    @Column(nullable = false)
    private long frequency = 1;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
