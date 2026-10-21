package com.meterengine.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class Organization {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  protected Organization() {}

  public String getName() {
    return name;
  }
}
