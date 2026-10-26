package com.meterengine.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class UserAccount implements Persistable<UUID> {

  public static final String MEMBER = "member";
  public static final String OPERATOR = "operator";

  public static final String ROLE_PATTERN = "member|operator";

  public static final int NAME_MAX_LENGTH = 255;
  public static final int EMAIL_MAX_LENGTH = 254;
  public static final String EMAIL_PATTERN = ".+@.+\\..+";
  public static final String EMAIL_FORMAT_MESSAGE = "올바른 형식의 이메일 주소여야 합니다";

  public static final int PASSWORD_MIN_LENGTH = 8;
  public static final int PASSWORD_MAX_LENGTH = 72;
  public static final int PASSWORD_MAX_BYTES = 72;

  @Id private UUID id;

  @Column(nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Transient private boolean isNew = true;

  protected UserAccount() {}

  public UserAccount(
      UUID id,
      String email,
      String passwordHash,
      String name,
      String role,
      OffsetDateTime createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.name = name;
    this.role = role;
    this.createdAt = createdAt;
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getName() {
    return name;
  }

  public String getRole() {
    return role;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void changeRole(String role) {
    this.role = role;
  }

  public void changePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }
}
