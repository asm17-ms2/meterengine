package com.meterengine.liveenvironment.entity;

import com.meterengine.global.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class LiveEnvironmentApplication implements Persistable<UUID> {

  public static final String PENDING = "pending";
  public static final String APPROVED = "approved";
  public static final String REJECTED = "rejected";

  public static final String STATUS_PATTERN = "pending|approved|rejected";
  public static final String REVIEW_STATUS_PATTERN = "approved|rejected";

  public static final String BUSINESS_REGISTRATION_NUMBER_PATTERN = "[0-9]{10}";
  public static final int MERCHANT_ID_MAX_LENGTH = 64;
  public static final int REJECTION_REASON_MAX_LENGTH = 500;

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "business_registration_number", nullable = false, updatable = false)
  private String businessRegistrationNumber;

  @Column(name = "toss_payments_merchant_id", nullable = false, updatable = false)
  private String tossPaymentsMerchantId;

  // 검토 중인 신청만 실결제 키를 갖는다. 검토가 끝나면 지운다.
  @Column(name = "toss_payments_client_key")
  private String tossPaymentsClientKey;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "toss_payments_secret_key")
  private String tossPaymentsSecretKey;

  @Column(nullable = false)
  private String status;

  @Column(name = "business_status", nullable = false, updatable = false)
  private String businessStatus;

  @Column(name = "tax_type", updatable = false)
  private String taxType;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "applicant_user_account_id", nullable = false, updatable = false)
  private UUID applicantUserAccountId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "reviewer_user_account_id")
  private UUID reviewerUserAccountId;

  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  // 상점아이디 확인은 JDBC로 한 번만 쓴다. 엔티티는 읽기만 한다.
  @Column(name = "observed_toss_payments_merchant_id", insertable = false, updatable = false)
  private String observedTossPaymentsMerchantId;

  @Column(name = "merchant_id_checked_at", insertable = false, updatable = false)
  private OffsetDateTime merchantIdCheckedAt;

  @Transient private boolean isNew = true;

  protected LiveEnvironmentApplication() {}

  public LiveEnvironmentApplication(
      UUID id,
      UUID organizationId,
      String businessRegistrationNumber,
      String tossPaymentsMerchantId,
      String tossPaymentsClientKey,
      String tossPaymentsSecretKey,
      String businessStatus,
      String taxType,
      UUID applicantUserAccountId,
      OffsetDateTime createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.businessRegistrationNumber = businessRegistrationNumber;
    this.tossPaymentsMerchantId = tossPaymentsMerchantId;
    this.tossPaymentsClientKey = tossPaymentsClientKey;
    this.tossPaymentsSecretKey = tossPaymentsSecretKey;
    this.status = PENDING;
    this.businessStatus = businessStatus;
    this.taxType = taxType;
    this.applicantUserAccountId = applicantUserAccountId;
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

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getBusinessRegistrationNumber() {
    return businessRegistrationNumber;
  }

  public String getTossPaymentsMerchantId() {
    return tossPaymentsMerchantId;
  }

  public String getTossPaymentsClientKey() {
    return tossPaymentsClientKey;
  }

  public String getTossPaymentsSecretKey() {
    return tossPaymentsSecretKey;
  }

  public String getStatus() {
    return status;
  }

  public String getBusinessStatus() {
    return businessStatus;
  }

  public String getTaxType() {
    return taxType;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getReviewedAt() {
    return reviewedAt;
  }

  public String getObservedTossPaymentsMerchantId() {
    return observedTossPaymentsMerchantId;
  }

  public OffsetDateTime getMerchantIdCheckedAt() {
    return merchantIdCheckedAt;
  }

  public boolean isPending() {
    return PENDING.equals(status);
  }

  public void approve(UUID reviewerUserAccountId, OffsetDateTime reviewedAt) {
    review(APPROVED, reviewerUserAccountId, reviewedAt);
  }

  public void reject(UUID reviewerUserAccountId, OffsetDateTime reviewedAt, String reason) {
    review(REJECTED, reviewerUserAccountId, reviewedAt);
    this.rejectionReason = reason;
  }

  private void review(String nextStatus, UUID reviewerUserAccountId, OffsetDateTime reviewedAt) {
    if (!isPending()) {
      throw new IllegalStateException(status + " -> " + nextStatus);
    }
    this.status = nextStatus;
    this.tossPaymentsClientKey = null;
    this.tossPaymentsSecretKey = null;
    this.reviewerUserAccountId = reviewerUserAccountId;
    this.reviewedAt = reviewedAt;
  }
}
