package com.ankur.design.rest.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ErrorDto {
private String message;
private String status;

  public ErrorDto(String message, String status) {
    this.message = message;
    this.status = status;
  }

  public ErrorDto() {
  }
}
