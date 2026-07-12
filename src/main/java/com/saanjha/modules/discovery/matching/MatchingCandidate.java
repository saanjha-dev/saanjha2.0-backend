package com.saanjha.modules.discovery.matching;

import java.util.UUID;

public record MatchingCandidate(UUID userId, String displayName, MatchingScore score) {}
