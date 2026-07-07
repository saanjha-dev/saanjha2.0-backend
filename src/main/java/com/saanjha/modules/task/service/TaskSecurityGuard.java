package com.saanjha.modules.task.service;

import com.saanjha.modules.task.entity.Task;
import com.saanjha.modules.task.repository.TaskRepository;
import com.saanjha.modules.team.service.TeamSecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resource-level authorization guard for the Task module. Composes
 * {@code TeamSecurityGuard} for membership checks rather than duplicating
 * "is this user on this project's team" logic — same reuse discipline
 * already established by {@code ApplicationSecurityGuard}.
 */
@Component("taskGuard")
@RequiredArgsConstructor
public class TaskSecurityGuard {

    private final TaskRepository taskRepository;
    private final TeamSecurityGuard teamSecurityGuard;

    /** True if the given user is a live member of the team that owns this task's project. */
    public boolean isTeamMemberOfTask(UUID taskId, String userIdText) {
        if (taskId == null || userIdText == null) {
            return false;
        }
        return taskRepository.findById(taskId)
                .map(Task::getProjectId)
                .map(projectId -> teamSecurityGuard.isMemberOfProjectsTeam(projectId, userIdText))
                .orElse(false);
    }

    /** True if the given user is a live member of the team for this project (no taskId in hand yet, e.g. creating a task). */
    public boolean isTeamMemberOfProject(UUID projectId, String userIdText) {
        return teamSecurityGuard.isMemberOfProjectsTeam(projectId, userIdText);
    }

    /** True if the given user is this task's current assignee. */
    public boolean isAssignee(UUID taskId, String userIdText) {
        if (taskId == null || userIdText == null) {
            return false;
        }
        return taskRepository.findById(taskId)
                .map(Task::getAssigneeId)
                .map(assigneeId -> assigneeId.toString().equalsIgnoreCase(userIdText))
                .orElse(false);
    }
}
