package com.saanjha.modules.contribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class ContributionRequestDTOs {

    public record CorrectionRequest(
            @NotBlank(message = "A reason is required to issue a correction")
            @Size(max = 500)
            String reason
    ) {}

    public record UpdateScoringWeightRequest(
            @NotBlank(message = "Contribution type is required")
            String contributionType,

            @DecimalMin(value = "0.0", message = "Base weight cannot be negative")
            double baseWeight
    ) {}

    public record ManualSnapshotRequest(
            UUID userId
    ) {}
}
