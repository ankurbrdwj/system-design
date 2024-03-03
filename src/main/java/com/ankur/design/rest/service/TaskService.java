package com.ankur.design.rest.service;

import com.ankur.design.rest.dto.TaskDto;
import com.ankur.design.rest.domain.Task;
import com.ankur.design.rest.exception.ElementNotFoundException;
import com.ankur.design.rest.repository.TaskRepository;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
  private final TaskRepository taskRepository;
  private final String broker;




  public TaskService(TaskRepository taskRepository
    , @Value("${spring.broker.location}") String brokerLocation) {
    this.taskRepository = taskRepository;
    this.broker="GCP".equals(brokerLocation)?"gcpLib":"onpremlib";
  }

  public TaskDto updateTask(Long id, TaskDto task) throws ElementNotFoundException {
    String broker1 = this.broker;
    Task taskEntity = taskRepository.findById(id)
      .orElseThrow(()-> new ElementNotFoundException("Cannot find task with given id"));
    taskEntity.setDescription(task.getDescription());
    taskEntity.setPriority(task.getPriority());
    taskEntity= taskRepository.save(taskEntity);
    return new TaskDto(taskEntity.getId(),taskEntity.getDescription(),taskEntity.getPriority());
  }
}
