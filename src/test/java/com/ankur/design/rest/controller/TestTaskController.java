package com.ankur.design.rest.controller;

import com.ankur.design.rest.dto.TaskDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class TestTaskController {
  // This is integration Test with SpringBootTest
  @Autowired
  private MockMvc mockMvc;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("put tasks/{id} and return 404 with message json")
  void shouldReturn404WithMessageJson() throws Exception {
    TaskDto dto = new TaskDto(100L,"task description",10L);
    long id = 100L;
    String json="{\n" +
      "\"description\":\"task description\",\n" +
      "\"priority\":\"10\"\n" +
      "}";
    MvcResult result= mockMvc
      .perform(put("/tasks/{id}",id)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content(objectMapper.writeValueAsString(dto)))
      .andExpect(status().is4xxClientError()).andReturn();
      String content = result.getResponse().getContentAsString();
  }
}
