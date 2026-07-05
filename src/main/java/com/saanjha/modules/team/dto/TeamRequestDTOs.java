package com.saanjha.modules.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class TeamRequestDTOs {

    public record TransferLeadershipRequest(
            @NotNull(message = "New lead user id is required")
            UUID newLeadUserId
    ) {}

    public record RemoveMemberRequest(
            @Size(max = 500, message = "Reason cannot exceed 500 characters")
            String reason
    ) {}

    public record SuspendMemberRequest(
            @NotBlank(message = "A reason is required to suspend a member")
            @Size(max = 500)
            String reason
    ) {}

    public record UpdateSettingsRequest(
            @Pattern(regexp = "^(PUBLIC|MEMBERS_ONLY|PRIVATE)$")
            String visibility,

            Boolean guestAccessEnabled,

            @Pattern(regexp = "^(PUBLIC|MEMBERS_ONLY|LEAD_ONLY)$")
            String activityVisibility,

            @Pattern(regexp = "^(LEAD_ONLY|ANY_MEMBER)$")
            String memberInvitationPolicy
    ) {}

    public record DissolveTeamRequest(
            @NotBlank(message = "A reason is required to dissolve a team")
            @Size(max = 500)
            String reason
    ) {}

    public record LockTeamRequest(
            @Size(max = 500)
            String reason
    ) {}
}
