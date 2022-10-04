package com.ankur.design.rest.service;

import com.ankur.design.rest.dto.TaskDto;
import com.ankur.design.booking.recruitment.hotel.exception.ElementNotFoundException;
import com.ankur.design.rest.domain.Task;
import com.ankur.design.rest.repository.TaskRepository;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
  private final TaskRepository taskRepository;

  public TaskService(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  public TaskDto updateTask(Long id, TaskDto task) {
    Task taskEntity = taskRepository.findById(id)
      .orElseThrow(()-> new ElementNotFoundException("Cannot find task with given id"));
    taskEntity.setDescription(task.getDescription());
    taskEntity.setPriority(task.getPriority());
    taskEntity= taskRepository.save(taskEntity);
    return new TaskDto(taskEntity.getId(),taskEntity.getDescription(),taskEntity.getPriority());
  }
}
