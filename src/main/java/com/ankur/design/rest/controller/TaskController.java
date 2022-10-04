package com.ankur.design.rest.controller;

import com.ankur.design.rest.dto.TaskDto;
import com.ankur.design.rest.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.logging.Logger;

@RestController
@RequestMapping("/tasks")
public class TaskController {
  private static Logger log = Logger.getLogger("Solution");
  private final TaskService taskService;

  public TaskController(TaskService taskService) {
    this.taskService = taskService;
  }

  @PutMapping("/{id}")
  public TaskDto createTask(@PathVariable Long id, @RequestBody TaskDto task) {
    return taskService.updateTask(id,task);
  }
}
