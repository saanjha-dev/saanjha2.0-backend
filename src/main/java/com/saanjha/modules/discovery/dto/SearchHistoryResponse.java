package com.saanjha.modules.discovery.dto;

import java.time.Instant;
import java.util.UUID;

public record SearchHistoryResponse(UUID id, String queryText, int resultCount, Instant searchedAt) {}
