package com.ankur.design.rest.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.Objects;

@Entity
public class Task {
  private static final long serialVersionUID = 3252591505029724236L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String description;
  private Long priority;

  public Task() {
  }

  public Task(Long id, String description, Long priority) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Task task = (Task) o;
    return Objects.equals(id, task.id) && Objects.equals(description, task.description) && Objects.equals(priority, task.priority);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, description, priority);
  }

  @Override
  public String toString() {
    return "Task{" +
      "id=" + id +
      ", description='" + description + '\'' +
      ", priority=" + priority +
      '}';
  }
}
