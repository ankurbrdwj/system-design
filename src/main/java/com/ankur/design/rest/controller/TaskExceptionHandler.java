package com.ankur.design.rest.controller;

import com.ankur.design.rest.dto.ErrorDto;
import com.ankur.design.rest.exception.ElementNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class TaskExceptionHandler {
  @ResponseBody
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(ElementNotFoundException.class)
  public ErrorDto handleHttpElementNotFoundException(ElementNotFoundException ex) {
    ErrorDto error = new ErrorDto();
    error.setMessage(ex.getMessage());
    error.setStatus(HttpStatus.NOT_FOUND.toString());
    return error;
  }

}
