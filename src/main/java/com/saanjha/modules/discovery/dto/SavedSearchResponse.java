package com.saanjha.modules.discovery.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SavedSearchResponse(
        UUID id,
        String name,
        String queryText,
        Map<String, Object> filters,
        Instant lastRunAt,
        Instant createdAt
) {}
