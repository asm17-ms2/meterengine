package com.meterengine.account.entity;

import com.meterengine.organization.api.OrganizationPerson;
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

  public static final int NAME_MAX_LENGTH = OrganizationPerson.NAME_MAX_LENGTH;
  public static final int EMAIL_MAX_LENGTH = OrganizationPerson.EMAIL_MAX_LENGTH;
  public static final String EMAIL_PATTERN = OrganizationPerson.EMAIL_PATTERN;
  public static final String EMAIL_FORMAT_MESSAGE = OrganizationPerson.EMAIL_FORMAT_MESSAGE;

  public static final int PASSWORD_MIN_LENGTH = OrganizationPerson.PASSWORD_MIN_LENGTH;
  public static final int PASSWORD_MAX_LENGTH = OrganizationPerson.PASSWORD_MAX_LENGTH;
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

  @Column(name = "consent_required", nullable = false)
  private boolean consentRequired;

  @Column(name = "email_verified_at")
  private OffsetDateTime emailVerifiedAt;

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
    // 운영자가 만든 계정과 초대로 들어온 계정은 이메일을 따로 인증하지 않는다. 가입만 requireEmailVerification으로 되돌린다.
    this.emailVerifiedAt = createdAt;
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

  public boolean isConsentRequired() {
    return consentRequired;
  }

  public void requireConsent() {
    consentRequired = true;
  }

  public void acceptConsent() {
    consentRequired = false;
  }

  public OffsetDateTime getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public boolean isEmailVerified() {
    return emailVerifiedAt != null;
  }

  public void requireEmailVerification() {
    emailVerifiedAt = null;
  }

  public void verifyEmail(OffsetDateTime verifiedAt) {
    if (emailVerifiedAt == null) emailVerifiedAt = verifiedAt;
  }

  public void changeRole(String role) {
    this.role = role;
  }

  public void changePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }
}
