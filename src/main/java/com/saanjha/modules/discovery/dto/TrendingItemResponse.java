package com.saanjha.modules.discovery.dto;

public record TrendingItemResponse(String entityType, String entityKey, double score, int rank) {}
