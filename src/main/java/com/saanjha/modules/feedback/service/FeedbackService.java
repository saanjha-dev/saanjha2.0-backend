package com.saanjha.modules.feedback.service;

import com.saanjha.modules.feedback.dto.FeedbackRequestDTOs.CreateFeedbackRequest;
import com.saanjha.modules.feedback.entity.Feedback;
import com.saanjha.modules.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    @Transactional
    public void submitFeedback(UUID userId, CreateFeedbackRequest request) {
        log.info("User {} submitting feedback in category: {}", userId, request.category());
        
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setCategory(request.category());
        feedback.setRating(request.rating());
        feedback.setContent(request.content());
        feedback.setPageUrl(request.pageUrl());

        feedbackRepository.save(feedback);
    }
}
