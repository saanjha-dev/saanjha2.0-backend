package com.saanjha.modules.discovery.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Personal search history — only ever written for authenticated callers.
 * Never logged for anonymous search, since Discovery has no session/device
 * identity of its own to attach an anonymous entry to.
 */
@Entity
@Table(name = "dsc_search_history", schema = "dsc")
@Getter
@Setter
public class SearchHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "query_text", length = 500)
    private String queryText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String filters = "{}";

    @Column(name = "result_count", nullable = false)
    private int resultCount = 0;

    @Column(name = "searched_at", nullable = false)
    private Instant searchedAt = Instant.now();
}
