package com.meterengine.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/**
 * 고객 (MS2-129).
 *
 * <p>스키마의 정본은 Flyway 마이그레이션이고 이 엔티티는 그것을 따라간다. 둘이 어긋나면 {@code
 * spring.jpa.hibernate.ddl-auto=validate}가 기동 때 잡는다.
 *
 * <p>organization_id를 {@code @ManyToOne}이 아니라 UUID 컬럼으로 두는 이유: 도입사 자체를 다루는 코드가 아직 없어 Organization
 * 엔티티가 없고, 조회가 늘 도입사 하나로 좁혀지는 구조라 테넌트 스코프를 조건으로 명시하는 편이 격리를 눈에 보이게 한다.
 *
 * <p>equals/hashCode를 두지 않는다. 이 엔티티로 컬렉션 비교나 병합을 하지 않아서, JPA 엔티티의 동일성 규칙을 정하는 논의를 지금 끌어올 필요가 없다.
 */
@Entity
public class Customer {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false)
  private String name;

  /** Hibernate 전용. */
  protected Customer() {}

  public Customer(UUID id, UUID organizationId, String name) {
    this.id = id;
    this.organizationId = organizationId;
    this.name = name;
  }

  /**
   * 이름을 바꾼다 (MS2-155).
   *
   * <p>고칠 수 있는 것이 이름뿐이라 setter 대신 이 이름을 쓴다.
   *
   * <p>{@code public}인 것은 MS2-149가 엔티티와 서비스를 다른 패키지로 갈라 놓아서다. 쓰는 곳은 {@code
   * com.meterengine.customer.service.CustomerService} 하나다.
   */
  public void rename(String name) {
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getName() {
    return name;
  }
}
