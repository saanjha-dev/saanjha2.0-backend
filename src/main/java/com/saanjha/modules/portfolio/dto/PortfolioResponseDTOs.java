package com.saanjha.modules.portfolio.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class PortfolioResponseDTOs {

    /** The recruiter-facing public page — one project showcase per completed project. */
    public record PortfolioEntryResponse(
            UUID id,
            UUID projectId,
            String projectTitle,
            String projectSlug,
            String projectCategory,
            String projectDescriptionExcerpt,
            List<String> technologies,
            String role,
            boolean wasLead,
            String contributionTitle,
            Instant joinedAt,
            Instant leftAt,
            Long tenureDays,
            double contributionScore,
            int tasksCompleted,
            int reviewsGiven,
            String verificationStatus,
            Instant completedAt,
            Instant generatedAt
    ) {}

    public record PortfolioSummaryResponse(
            UUID userId,
            int projectsCompleted,
            int leadershipStints,
            double totalVerifiedScore,
            Double reliabilityScore,
            Double leadershipScore,
            Double consistencyScore,
            Double reviewQualityScore,
            Instant lastGeneratedAt
    ) {}

    /** The full public portfolio view: summary + showcases + badges, in one payload. */
    public record PublicPortfolioResponse(
            PortfolioSummaryResponse summary,
            List<PortfolioEntryResponse> entries,
            List<BadgeResponse> badges
    ) {}

    public record BadgeResponse(
            String badgeType,
            Instant awardedAt
    ) {}

    public record TimelineEntryResponse(
            UUID id,
            UUID projectId,
            String eventType,
            String description,
            Instant occurredAt
    ) {}

    public record VisibilityResponse(
            UUID userId,
            String visibility,
            String shareToken
    ) {}

    public record InsightsResponse(
            List<TechnologyCount> mostUsedTechnologies,
            List<CategoryCount> projectCategories,
            long longestProjectTenureDays,
            double averageProjectTenureDays
    ) {}

    public record TechnologyCount(String technology, long count) {}

    public record CategoryCount(String category, long count) {}
}
