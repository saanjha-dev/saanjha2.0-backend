package com.saanjha.modules.project.service;

import com.saanjha.modules.project.dto.ProjectRequestDTOs.*;
import com.saanjha.modules.project.dto.ProjectResponseDTOs.ProjectResponse;
import com.saanjha.modules.project.entity.Project;
import com.saanjha.modules.project.entity.ProjectRequirement;
import com.saanjha.modules.project.entity.ProjectStatus;
import com.saanjha.modules.project.repository.ProjectRepository;
import com.saanjha.modules.project.repository.ProjectRequirementRepository;
import com.saanjha.modules.project.repository.ProjectStatusLogRepository;
import com.saanjha.modules.project.repository.ProjectTagRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectRequirementRepository requirementRepository;
    @Mock private ProjectTagRepository tagRepository;
    @Mock private ProjectStatusLogRepository statusLogRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ProjectService projectService;

    private UUID leadUserId;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(
                projectRepository, requirementRepository, tagRepository, statusLogRepository, eventPublisher);
        leadUserId = UUID.randomUUID();
        // save() is stubbed by every mutating test that needs it; kept lenient here to avoid repetition.
        lenient().when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ========================================================================
    // CREATION
    // ========================================================================

    @Test
    void createProject_initializesAsDraftWithGeneratedSlug() {
        when(projectRepository.existsBySlugIgnoreCase(any())).thenReturn(false);
        CreateProjectRequest request = new CreateProjectRequest("Realtime Chat App", "A project about building a chat app.", "WEB", 5);

        ProjectResponse response = projectService.createProject(leadUserId, request);

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.leadUserId()).isEqualTo(leadUserId);
        assertThat(response.slug()).startsWith("realtime-chat-app-");
        verifyNoInteractions(eventPublisher); // Creating a DRAFT is CRUD, not a business event.
    }

    @Test
    void createProject_regeneratesSlugOnCollision() {
        when(projectRepository.existsBySlugIgnoreCase(any())).thenReturn(true, true, false);
        CreateProjectRequest request = new CreateProjectRequest("Chat App", "Description text here for the project.", "WEB", 5);

        projectService.createProject(leadUserId, request);

        verify(projectRepository, times(3)).existsBySlugIgnoreCase(any());
    }

    // ========================================================================
    // READ-ONLY GUARD (PROJECT_READ_ONLY)
    // ========================================================================

    @Test
    void updateScope_onCompletedProject_throwsProjectReadOnly() {
        Project completed = draftProject();
        completed.setStatus(ProjectStatus.COMPLETED);
        when(projectRepository.findById(completed.getId())).thenReturn(Optional.of(completed));

        UpdateProjectRequest request = new UpdateProjectRequest("New Title", null, null, null, null);

        assertThatThrownBy(() -> projectService.updateScope(completed.getId(), request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.PROJECT_READ_ONLY));
    }

    @Test
    void updateScope_onArchivedProject_throwsProjectReadOnly() {
        Project archived = draftProject();
        archived.setStatus(ProjectStatus.ARCHIVED);
        when(projectRepository.findById(archived.getId())).thenReturn(Optional.of(archived));

        UpdateProjectRequest request = new UpdateProjectRequest("New Title", null, null, null, null);

        assertThatThrownBy(() -> projectService.updateScope(archived.getId(), request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.PROJECT_READ_ONLY));
    }

    // ========================================================================
    // VISIBILITY
    // ========================================================================

    @Test
    void getProject_draftNotOwnedByRequester_returnsNotFound() {
        Project draft = draftProject();
        when(projectRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> projectService.getProject(draft.getId(), UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void getProject_draftOwnedByRequester_isVisible() {
        Project draft = draftProject();
        when(projectRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        ProjectResponse response = projectService.getProject(draft.getId(), leadUserId);

        assertThat(response.id()).isEqualTo(draft.getId());
    }

    @Test
    void getProject_recruitingProject_isVisibleToAnyone() {
        Project recruiting = draftProject();
        recruiting.setStatus(ProjectStatus.RECRUITING);
        when(projectRepository.findById(recruiting.getId())).thenReturn(Optional.of(recruiting));

        ProjectResponse response = projectService.getProject(recruiting.getId(), null);

        assertThat(response.id()).isEqualTo(recruiting.getId());
    }

    // ========================================================================
    // PUBLISH GATE (RECRUITING PRE-CONDITIONS)
    // ========================================================================

    @Test
    void transitionToRecruiting_withoutRequirements_isRejected() {
        Project draft = draftProject();
        draft.setDescription("A".repeat(60)); // satisfies min length, but zero requirements
        when(projectRepository.findWithLockById(draft.getId())).thenReturn(Optional.of(draft));

        UpdateProjectStatusRequest request = new UpdateProjectStatusRequest("RECRUITING", null);

        assertThatThrownBy(() -> projectService.transitionStatus(draft.getId(), leadUserId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void transitionToRecruiting_withShortDescription_isRejected() {
        Project draft = draftProject();
        draft.setDescription("Too short");
        draft.addRequirement(requirement("Java"));
        when(projectRepository.findWithLockById(draft.getId())).thenReturn(Optional.of(draft));

        UpdateProjectStatusRequest request = new UpdateProjectStatusRequest("RECRUITING", null);

        assertThatThrownBy(() -> projectService.transitionStatus(draft.getId(), leadUserId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void transitionToRecruiting_withValidScope_publishesEventAndSetsTimestamp() {
        Project draft = draftProject();
        draft.setDescription("A".repeat(60));
        draft.addRequirement(requirement("Java"));
        when(projectRepository.findWithLockById(draft.getId())).thenReturn(Optional.of(draft));

        UpdateProjectStatusRequest request = new UpdateProjectStatusRequest("RECRUITING", null);
        ProjectResponse response = projectService.transitionStatus(draft.getId(), leadUserId, request);

        assertThat(response.status()).isEqualTo("RECRUITING");
        assertThat(response.recruitingStartedAt()).isNotNull();
        verify(statusLogRepository).save(any());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .anySatisfy(event -> assertThat(event.getClass().getSimpleName()).isEqualTo("ProjectStatusChangedEvent"))
                .anySatisfy(event -> assertThat(event.getClass().getSimpleName()).isEqualTo("ProjectPublishedEvent"));
    }

    // ========================================================================
    // LOCK-TEAM GATE (MIN TEAM SIZE)
    // ========================================================================

    @Test
    void transitionToInProgress_withSoloTeam_isRejected() {
        Project recruiting = draftProject();
        recruiting.setStatus(ProjectStatus.RECRUITING);
        recruiting.setCurrentTeamSize(1);
        when(projectRepository.findWithLockById(recruiting.getId())).thenReturn(Optional.of(recruiting));

        UpdateProjectStatusRequest request = new UpdateProjectStatusRequest("IN_PROGRESS", null);

        assertThatThrownBy(() -> projectService.transitionStatus(recruiting.getId(), leadUserId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    // ========================================================================
    // ILLEGAL TRANSITIONS DELEGATE TO THE VALIDATOR
    // ========================================================================

    @Test
    void transitionFromCompleted_isAlwaysRejected() {
        Project completed = draftProject();
        completed.setStatus(ProjectStatus.COMPLETED);
        when(projectRepository.findWithLockById(completed.getId())).thenReturn(Optional.of(completed));

        UpdateProjectStatusRequest request = new UpdateProjectStatusRequest("ARCHIVED", "cleanup");

        assertThatThrownBy(() -> projectService.transitionStatus(completed.getId(), leadUserId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    // ========================================================================
    // REQUIREMENTS
    // ========================================================================

    @Test
    void addRequirement_duplicateSkill_throwsConflict() {
        Project draft = draftProject();
        when(projectRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(requirementRepository.existsByProject_IdAndRoleNameIgnoreCase(draft.getId(), "Java")).thenReturn(true);

        AddRequirementRequest request = new AddRequirementRequest("Java", java.util.List.of(), "INTERMEDIATE", 2);

        assertThatThrownBy(() -> projectService.addRequirement(draft.getId(), request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Project draftProject() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setLeadUserId(leadUserId);
        project.setTitle("Test Project");
        project.setDescription("Initial description.");
        project.setCategory("WEB");
        project.setMaxTeamSize(5);
        project.setCurrentTeamSize(1);
        project.setSlug("test-project-abc123");
        project.setStatus(ProjectStatus.DRAFT);
        project.setRequirements(new HashSet<>());
        project.setTags(new HashSet<>());
        return project;
    }

    private ProjectRequirement requirement(String roleName) {
        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setId(UUID.randomUUID());
        requirement.setRoleName(roleName);
        requirement.setSkillLevel("INTERMEDIATE");
        requirement.setSlotsAvailable(1);
        return requirement;
    }
}
