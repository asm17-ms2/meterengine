package com.meterengine.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.domain.Persistable;

@Entity
public class Organization implements Persistable<UUID> {

  public static final String PENDING = "pending";
  public static final String ACTIVE = "active";
  public static final String REJECTED = "rejected";
  public static final String SUSPENDED = "suspended";

  public static final String STATUS_PATTERN = "pending|active|rejected|suspended";

  public static final int NAME_MAX_LENGTH = 255;

  private static final Map<String, Set<String>> NEXT_STATUSES =
      Map.of(
          PENDING, Set.of(ACTIVE, REJECTED),
          ACTIVE, Set.of(SUSPENDED),
          SUSPENDED, Set.of(ACTIVE));

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String status;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "business_registration_number")
  private String businessRegistrationNumber;

  @Column(name = "representative_name")
  private String representativeName;

  @Column(name = "business_address")
  private String businessAddress;

  // null이면 실제 환경이 잠겨 있다. 가입한 조직은 null로 시작하고 이용 신청이 승인되면 채운다.
  @Column(name = "live_environment_opened_at")
  private OffsetDateTime liveEnvironmentOpenedAt;

  @Transient private boolean isNew;

  protected Organization() {}

  public Organization(UUID id, String name, String status) {
    this.id = id;
    this.name = name;
    this.status = status;
    this.isNew = true;
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

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public boolean canTransitionTo(String nextStatus) {
    return NEXT_STATUSES.getOrDefault(status, Set.of()).contains(nextStatus);
  }

  public void transitionTo(String nextStatus) {
    if (!canTransitionTo(nextStatus)) {
      throw new IllegalStateException(status + " -> " + nextStatus);
    }
    this.status = nextStatus;
  }

  public OffsetDateTime getLiveEnvironmentOpenedAt() {
    return liveEnvironmentOpenedAt;
  }

  public boolean isLiveEnvironmentOpen() {
    return liveEnvironmentOpenedAt != null;
  }

  // 가입 대기 조직은 가입 승인을 받던 때의 서버가 만들어 열린 채 들어온다. 승인할 때 가입한 조직처럼 잠근다.
  public void lockLiveEnvironment() {
    this.liveEnvironmentOpenedAt = null;
  }

  public void openLiveEnvironment(OffsetDateTime openedAt) {
    if (liveEnvironmentOpenedAt == null) {
      this.liveEnvironmentOpenedAt = openedAt;
    }
  }

  public String getBusinessRegistrationNumber() {
    return businessRegistrationNumber;
  }

  public String getRepresentativeName() {
    return representativeName;
  }

  public String getBusinessAddress() {
    return businessAddress;
  }

  public void updateSupplier(
      String businessRegistrationNumber, String representativeName, String businessAddress) {
    this.businessRegistrationNumber = businessRegistrationNumber;
    this.representativeName = representativeName;
    this.businessAddress = businessAddress;
  }
}
