package com.ankur.design.rest.repository;

import com.ankur.design.rest.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
