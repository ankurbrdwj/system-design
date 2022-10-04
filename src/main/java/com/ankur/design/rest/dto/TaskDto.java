package com.ankur.design.rest.dto;

public class TaskDto {
  private Long id;
  private String description;
  private Long priority;

  public TaskDto() {
  }

  public TaskDto(Long id, String description, Long priority) {
    this.id = id;
    this.description = description;
    this.priority = priority;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Long getPriority() {
    return priority;
  }

  public void setPriority(Long priority) {
    this.priority = priority;
  }
}
