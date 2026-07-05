package com.saanjha.modules.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ApplicationRequestDTOs {

    public record SubmitApplicationRequest(
            @NotBlank(message = "A message to the project lead is required")
            @Size(max = 3000, message = "Message cannot exceed 3000 characters")
            String message,

            @Size(max = 100)
            String preferredRole,

            @Min(1) @Max(80)
            Integer weeklyHours,

            @Size(max = 50)
            String timezone
    ) {}

    public record ReviewDecisionRequest(
            @Size(max = 500, message = "Decision reason cannot exceed 500 characters")
            String reason
    ) {}

    public record AddNoteRequest(
            @NotBlank(message = "Note text is required")
            @Size(max = 2000, message = "Note cannot exceed 2000 characters")
            String note
    ) {}

    public record BulkReviewRequest(
            @NotBlank
            @Pattern(regexp = "^(SHORTLIST|ACCEPT|REJECT)$", message = "Action must be SHORTLIST, ACCEPT, or REJECT")
            String action,

            java.util.List<@NotBlank String> applicationIds,

            @Size(max = 500)
            String reason
    ) {}
}
