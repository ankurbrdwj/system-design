package com.ankur.design.rest.controller;

import com.ankur.design.rest.dto.ErrorDto;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TaskExceptionHandler {

  @ResponseBody
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(Exception.class)
  public String handleHttpElementNotFoundException(Exception ex) {
    ErrorDto error = new ErrorDto();
    error.setMessage(ex.getMessage());
    error.setStatus(HttpStatus.NOT_FOUND.toString());
    return error.toString();
  }

}
