package com.saanjha.modules.feedback.controller;

import com.saanjha.modules.feedback.dto.FeedbackRequestDTOs.CreateFeedbackRequest;
import com.saanjha.modules.feedback.service.FeedbackService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.ratelimit.RateLimit;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/feedback")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Feedback", description = "Endpoints for submitting feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @RateLimit(action = "submit-feedback", baseLimit = 5)
    @Operation(summary = "Submit Feedback", description = "Submits user feedback.")
    public ResponseEntity<ApiEnvelope<Void>> submitFeedback(@Valid @RequestBody CreateFeedbackRequest request) {
        feedbackService.submitFeedback(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(null));
    }
}
