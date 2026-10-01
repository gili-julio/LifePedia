package com.lifepedia.api.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskSubtaskRepository extends JpaRepository<TaskSubtask, UUID> {

    List<TaskSubtask> findByTaskIdInOrderByPositionAsc(Collection<UUID> taskIds);

    List<TaskSubtask> findByTaskIdOrderByPositionAsc(UUID taskId);
}
