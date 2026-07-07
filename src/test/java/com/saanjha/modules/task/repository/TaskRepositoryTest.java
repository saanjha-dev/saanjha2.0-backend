package com.saanjha.modules.task.repository;

import com.saanjha.modules.task.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the V13 migration's constraints against a real PostgreSQL
 * instance: the task-cannot-depend-on-itself check, the unique-dependency
 * constraint, and the unique-label-per-task constraint.
 */
@DataJpaTest
@Testcontainers
class TaskRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskDependencyRepository dependencyRepository;
    @Autowired private TaskLabelRepository labelRepository;

    @Test
    void savedTaskHasSafeDefaults() {
        Task task = newTask();
        Task saved = taskRepository.save(task);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.BACKLOG);
        assertThat(saved.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(saved.getActualHours()).isZero();
    }

    @Test
    void dependencyCannotReferenceItself() {
        Task task = taskRepository.save(newTask());

        assertThatThrownBy(() -> {
            dependencyRepository.save(new TaskDependency(task.getId(), task.getId(), DependencyType.BLOCKS, UUID.randomUUID()));
            dependencyRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateDependencyOfSameTypeIsRejected() {
        Task taskA = taskRepository.save(newTask());
        Task taskB = taskRepository.save(newTask());
        UUID actor = UUID.randomUUID();

        dependencyRepository.save(new TaskDependency(taskA.getId(), taskB.getId(), DependencyType.BLOCKS, actor));

        assertThatThrownBy(() -> {
            dependencyRepository.save(new TaskDependency(taskA.getId(), taskB.getId(), DependencyType.BLOCKS, actor));
            dependencyRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameTwoTasksCanHaveDifferentDependencyTypes() {
        Task taskA = taskRepository.save(newTask());
        Task taskB = taskRepository.save(newTask());
        UUID actor = UUID.randomUUID();

        dependencyRepository.save(new TaskDependency(taskA.getId(), taskB.getId(), DependencyType.BLOCKS, actor));
        TaskDependency second = dependencyRepository.save(new TaskDependency(taskA.getId(), taskB.getId(), DependencyType.RELATES_TO, actor));

        assertThat(second.getId()).isNotNull();
    }

    @Test
    void duplicateLabelOnSameTaskIsRejected() {
        Task task = taskRepository.save(newTask());
        labelRepository.save(new TaskLabel(task, "backend", "PROJECT"));

        assertThatThrownBy(() -> {
            labelRepository.save(new TaskLabel(task, "backend", "PROJECT"));
            labelRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Task newTask() {
        Task task = new Task();
        task.setProjectId(UUID.randomUUID());
        task.setTitle("Test task");
        task.setType(TaskType.FEATURE);
        task.setReporterId(UUID.randomUUID());
        return task;
    }
}
