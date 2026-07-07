package com.saanjha.modules.task.service;

import com.saanjha.modules.project.dto.ProjectResponseDTOs.ProjectSnapshot;
import com.saanjha.modules.project.service.ProjectService;
import org.mockito.ArgumentCaptor;
import com.saanjha.modules.task.dto.TaskRequestDTOs.*;
import com.saanjha.modules.task.dto.TaskResponseDTOs.TaskResponse;
import com.saanjha.modules.task.entity.*;
import com.saanjha.modules.task.repository.*;
import com.saanjha.modules.team.dto.TeamResponseDTOs.TeamResponse;
import com.saanjha.modules.team.service.TeamSecurityGuard;
import com.saanjha.modules.team.service.TeamService;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ChecklistItemRepository checklistItemRepository;
    @Mock private TaskLabelRepository labelRepository;
    @Mock private TaskDependencyRepository dependencyRepository;
    @Mock private TaskWatcherRepository watcherRepository;
    @Mock private TaskAttachmentRepository attachmentRepository;
    @Mock private TaskHistoryRepository historyRepository;
    @Mock private TaskActivityRepository activityRepository;
    @Mock private ProjectService projectService;
    @Mock private TeamService teamService;
    @Mock private TeamSecurityGuard teamSecurityGuard;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TaskService taskService;

    private UUID projectId;
    private UUID reporterId;
    private UUID assigneeId;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, checklistItemRepository, labelRepository, dependencyRepository,
                watcherRepository, attachmentRepository, historyRepository, activityRepository,
                projectService, teamService, teamSecurityGuard, eventPublisher);
        projectId = UUID.randomUUID();
        reporterId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        lenient().when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProjectSnapshot inProgressSnapshot() {
        return new ProjectSnapshot(projectId, reporterId, "IN_PROGRESS", "PUBLIC", 5, 3);
    }

    private TeamResponse activeTeam() {
        return new TeamResponse(UUID.randomUUID(), projectId, "ACTIVE", null, null, null, null, null, null, null);
    }

    private CreateTaskRequest defaultCreateRequest() {
        return new CreateTaskRequest("Build the login page", "Some details", "FEATURE", "HIGH", null, 3, 5.0, null);
    }

    // ========================================================================
    // CREATION
    // ========================================================================

    @Test
    void createTask_happyPath_succeeds() {
        when(projectService.getSnapshot(projectId)).thenReturn(inProgressSnapshot());
        when(teamService.getTeamByProject(projectId)).thenReturn(activeTeam());

        TaskResponse response = taskService.createTask(projectId, reporterId, defaultCreateRequest());

        assertThat(response.status()).isEqualTo("BACKLOG");
        assertThat(response.type()).isEqualTo("FEATURE");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void createTask_forCompletedProject_isRejected() {
        when(projectService.getSnapshot(projectId)).thenReturn(new ProjectSnapshot(projectId, reporterId, "COMPLETED", "PUBLIC", 5, 3));

        assertThatThrownBy(() -> taskService.createTask(projectId, reporterId, defaultCreateRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.PROJECT_READ_ONLY));
    }

    @Test
    void createTask_forArchivedTeam_isRejected() {
        when(projectService.getSnapshot(projectId)).thenReturn(inProgressSnapshot());
        when(teamService.getTeamByProject(projectId)).thenReturn(
                new TeamResponse(UUID.randomUUID(), projectId, "ARCHIVED", null, null, null, null, null, null, null));

        assertThatThrownBy(() -> taskService.createTask(projectId, reporterId, defaultCreateRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    @Test
    void createTask_withNonMemberAssignee_isRejected() {
        when(projectService.getSnapshot(projectId)).thenReturn(inProgressSnapshot());
        when(teamService.getTeamByProject(projectId)).thenReturn(activeTeam());
        when(teamSecurityGuard.isMemberOfProjectsTeam(projectId, assigneeId.toString())).thenReturn(false);

        CreateTaskRequest request = new CreateTaskRequest("Title", null, "BUG", null, assigneeId, null, null, null);

        assertThatThrownBy(() -> taskService.createTask(projectId, reporterId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    // ========================================================================
    // MOVE / STATE MACHINE BUSINESS RULES
    // ========================================================================

    @Test
    void move_toInProgress_withoutAssignee_isRejected() {
        Task task = backlogTask();
        when(taskRepository.findWithLockById(task.getId())).thenReturn(Optional.of(task));
        when(projectService.getSnapshot(projectId)).thenReturn(inProgressSnapshot());

        // First move to TODO succeeds conceptually; jump straight to IN_PROGRESS with no assignee.
        task.setStatus(TaskStatus.TODO);
        MoveTaskRequest request = new MoveTaskRequest("IN_PROGRESS", null);

        assertThatThrownBy(() -> taskService.move(task.getId(), reporterId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void move_toInProgress_atHoardingCap_isRejected() {
        Task task = backlogTask();
        task.setStatus(TaskStatus.TODO);
        task.setAssigneeId(assigneeId);
        when(taskRepository.findWithLockById(task.getId())).thenReturn(Optional.of(task));
        when(projectService.getSnapshot(projectId)).thenReturn(inProgressSnapshot());
        when(taskRepository.countByAssigneeIdAndStatus(assigneeId, TaskStatus.IN_PROGRESS)).thenReturn(3L);

        MoveTaskRequest request = new MoveTaskRequest("IN_PROGRESS", null);

        assertThatThrownBy(() -> taskService.move(task.getId(), reporterId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void move_toInProgress_underHoardingCap_succeeds() {
        Task task = backlogTask();
        task.setStatus(TaskStatus.TODO);
        task.setAssigneeId(assigneeId);
        when(taskRepository.findWithLockById(task.getId())).thenReturn(Optional.of(task));
        when(projectService.getSnapshot(projectId)).thenReturn(inProgressSnapshot());
        when(taskRepository.countByAssigneeIdAndStatus(assigneeId, TaskStatus.IN_PROGRESS)).thenReturn(2L);

        TaskResponse response = taskService.move(task.getId(), reporterId, new MoveTaskRequest("IN_PROGRESS", null));

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(task.getStartedAt()).isNotNull();
    }

    @Test
    void move_toBlocked_withoutReason_isRejected() {
        Task task = backlogTask();
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setAssigneeId(assigneeId);
        when(taskRepository.findWithLockById(task.getId())).thenReturn(Optional.of(task));
        when(projectService.getSnapshot(projectId)).thenReturn(inProgressSnapshot());

        assertThatThrownBy(() -> taskService.move(task.getId(), reporterId, new MoveTaskRequest("BLOCKED", null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void move_blockedDirectlyToDone_isStructurallyRejected() {
        Task task = backlogTask();
        task.setStatus(TaskStatus.BLOCKED);
        task.setAssigneeId(assigneeId);
        when(taskRepository.findWithLockById(task.getId())).thenReturn(Optional.of(task));
        when(projectService.getSnapshot(projectId)).thenReturn(inProgressSnapshot());

        assertThatThrownBy(() -> taskService.move(task.getId(), reporterId, new MoveTaskRequest("DONE", null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STATE_TRANSITION_FAILED));
    }

    @Test
    void move_reviewToDone_publishesRichTaskCompletedEvent() {
        Task task = backlogTask();
        task.setStatus(TaskStatus.IN_REVIEW);
        task.setAssigneeId(assigneeId);
        task.setStoryPoints(5);
        task.setEstimatedHours(10.0);
        task.setActualHours(12.0);
        when(taskRepository.findWithLockById(task.getId())).thenReturn(Optional.of(task));
        when(projectService.getSnapshot(projectId)).thenReturn(inProgressSnapshot());

        UUID reviewerId = UUID.randomUUID();
        taskService.move(task.getId(), reviewerId, new MoveTaskRequest("DONE", null));

        // 1. Capture the Object passed to publishEvent
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        // 2. Assert the type
        Object capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent).isInstanceOf(com.saanjha.modules.task.event.TaskEvents.TaskCompletedEvent.class);

        // 3. Safe cast and assert the properties
        var event = (com.saanjha.modules.task.event.TaskEvents.TaskCompletedEvent) capturedEvent;
        assertThat(event.complexity()).isEqualTo(5);
        assertThat(event.actualHours()).isEqualTo(12.0);
        assertThat(event.reviewedBy()).isEqualTo(reviewerId);
    }

    // ========================================================================
    // DEPENDENCY CYCLE PREVENTION
    // ========================================================================

    @Test
    void addDependency_toSelf_isRejected() {
        Task task = backlogTask();
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        AddDependencyRequest request = new AddDependencyRequest(task.getId(), "BLOCKS");

        assertThatThrownBy(() -> taskService.addDependency(task.getId(), reporterId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void addDependency_creatingACycle_isRejected() {
        Task taskA = backlogTask();
        Task taskB = backlogTask();
        taskB.setProjectId(taskA.getProjectId());

        when(taskRepository.findById(taskA.getId())).thenReturn(Optional.of(taskA));
        when(taskRepository.findById(taskB.getId())).thenReturn(Optional.of(taskB));
        when(dependencyRepository.existsByTaskIdAndRelatedTaskIdAndType(any(), any(), any())).thenReturn(false);

        // B already BLOCKS A (i.e. A depends on B). Now trying A BLOCKS B would create a 2-cycle.
        com.saanjha.modules.task.entity.TaskDependency existingEdge =
                new com.saanjha.modules.task.entity.TaskDependency(taskB.getId(), taskA.getId(), DependencyType.BLOCKS, reporterId);
        when(dependencyRepository.findByTaskIdAndType(taskB.getId(), DependencyType.BLOCKS)).thenReturn(java.util.List.of(existingEdge));

        AddDependencyRequest request = new AddDependencyRequest(taskB.getId(), "BLOCKS");

        assertThatThrownBy(() -> taskService.addDependency(taskA.getId(), reporterId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Task backlogTask() {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setProjectId(projectId);
        task.setTitle("Test task");
        task.setType(TaskType.FEATURE);
        task.setPriority(TaskPriority.MEDIUM);
        task.setStatus(TaskStatus.BACKLOG);
        task.setReporterId(reporterId);
        return task;
    }
}
