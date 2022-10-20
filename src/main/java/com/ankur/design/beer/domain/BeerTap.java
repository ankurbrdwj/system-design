package com.ankur.design.beer.domain;

import jakarta.persistence.Entity;

import java.time.LocalDateTime;

@Entity
public class BeerTap {
  private LocalDateTime opened_at;
  private LocalDateTime closed_at;

}
