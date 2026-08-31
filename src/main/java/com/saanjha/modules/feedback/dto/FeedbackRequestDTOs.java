package com.saanjha.modules.feedback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FeedbackRequestDTOs {

    public record CreateFeedbackRequest(
            @NotBlank(message = "Category is required")
            String category,

            @Min(value = 1, message = "Rating must be at least 1")
            @Max(value = 5, message = "Rating cannot exceed 5")
            Integer rating,

            @Size(max = 2000, message = "Feedback content cannot exceed 2000 characters")
            String content,

            String pageUrl
    ) {}
}
