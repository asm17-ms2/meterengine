package com.meterengine.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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

  /**
   * 등록 시각 (MS2-171). 값을 만드는 것은 DB이고 이 필드는 받아 오기만 한다.
   *
   * <p><b>{@code @Generated(INSERT)}인 이유.</b> 이 애노테이션은 INSERT 문에서 이 컬럼을 빼고, 같은 flush 안에서 {@code
   * INSERT ... RETURNING}으로 DB가 만든 값을 되읽어 이 필드에 채운다. 컬럼을 빼야 V3의 {@code DEFAULT clock_timestamp()}가
   * 발동한다. 매핑만 하고 이 애노테이션을 빼면 Hibernate가 전체 필드를 INSERT에 실어 {@code null}을 보내고 NOT NULL 위반이 난다.
   *
   * <p>{@code insertable = false}는 쓰지 않는다. 그쪽도 컬럼을 INSERT에서 빼지만 <b>되읽지 않아서</b>, {@code
   * CustomerService.create}가 돌려주는 인스턴스의 이 필드가 {@code null}로 남는다. 그 인스턴스가 그대로 등록 응답이 되므로 등록만 {@code
   * created_at: null}이 나가고 수정과 목록은 정상인 상태가 된다.
   *
   * <p><b>값을 만드는 자리를 자바에 두지 않는다.</b> setter도 초기화식도 두지 않고 생성자에서도 받지 않는다. 그래서 애노테이션이 어떤 이유로든 안 먹으면
   * INSERT가 {@code null}을 보내고 DB가 거절한다. 이 구조가 애노테이션 오류를 조용한 오작동이 아니라 빨간불로 만드는 전제다. <b>여기에 setter나
   * 초기화식을 더하면 그 전제가 조용히 깨진다.</b>
   *
   * <p><b>{@code updatable = false}의 지위.</b> 실효가 있다(실측). 이것이 있으면 {@code rename}이 내는 문장이 {@code
   * update customer set name=?,organization_id=? where id=?}이고, 빼면 {@code created_at=?}가 함께 실린다.
   *
   * <p>다만 <b>오늘 이 레포에서는 결과가 같다.</b> {@code rename}이 고치는 엔티티는 DB에서 읽어 온 것이라 실리는 값이 이미 저장된 값과 같기
   * 때문이다. 그래서 이것을 확인하는 단언은 어차피 실패할 수 없어 넣지 않았다. <b>즉 테스트가 이 애노테이션을 보증하지 않는다.</b>
   *
   * <p><b>이것이 막는 것과 못 막는 것.</b> 누가 이 필드를 메모리에서 고치면 DB는 안 바뀌지만 {@code rename}이 그 인스턴스를 그대로 응답에 실으므로
   * <b>응답과 DB가 갈린다.</b> 지금은 setter가 없어 도달할 수 없다. 그리고 ORM 밖의 UPDATE와 JPQL 벌크 업데이트는 이것으로 막히지 않는다.
   */
  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

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

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
