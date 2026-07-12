package com.saanjha.modules.discovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record SavedSearchRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String queryText,
        Map<String, Object> filters
) {}
