package com.meterengine.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.ErrorCodes;
import com.meterengine.TestcontainersConfiguration;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * 고객 CRUD API를 HTTP 계층부터 DB까지 관통해 검증한다 (MS2-155).
 *
 * <p>{@code @AutoConfigureMockMvc}를 쓰지 않고 WebApplicationContext에서 직접 만드는 이유는 {@code
 * EventIngestIntegrationTest} 참조. 컨텍스트를 공유해 Postgres 컨테이너가 한 번만 뜬다.
 *
 * <p>삭제 동시성은 여기서 보지 않는다. 이 클래스는 테스트마다 롤백되는 한 트랜잭션 안에서 도는데, 그 구조로는 두 트랜잭션이 겹치는 상황을 만들 수 없다. {@code
 * CustomerDeleteConcurrencyTest}가 커넥션을 두 개 써서 그것을 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CustomerCrudIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper jsonMapper;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  // --- 등록 ---

  @Test
  void 등록하면_201과_발급된_id와_등록시각이_오고_목록에_보인다() {
    UUID orgId = insertOrganization();
    OffsetDateTime beforePost = dbNow();

    MvcTestResult created =
        post(
            orgId,
            """
        {"name":"아크메 주식회사"}
        """);

    assertThat(created).hasStatus(201).bodyJson().extractingPath("$.name").isEqualTo("아크메 주식회사");
    assertThat(created).bodyJson().extractingPath("$.customer_id").asString().isNotEmpty();

    // created_at은 DB가 만들고 Hibernate가 INSERT ... RETURNING으로 되읽어 채운다 (MS2-171).
    // 이 단언이 없으면 @Generated를 빼거나 insertable=false로 바꿔도 전부 초록이고, 등록 응답만
    // null이 나가는 상태가 조용히 머지된다. 등록 경로가 merge를 타서(CustomerService.create의
    // javadoc 참조) 되읽기가 성립하는지도 여기서만 확인된다.
    //
    // 값이 POST 직전 시각보다 뒤인지까지 본다. 이것이 잡는 회귀는 V3의 마지막 문장(SET DEFAULT
    // clock_timestamp())이 사라져 앞 문장의 DEFAULT now()가 남는 경우다. 두 문장을 하나로 합치자는
    // 정리가 정확히 그 모양이 된다. 구분이 되는 이유는 이 클래스가 @Transactional이라 테스트 전체가
    // 한 트랜잭션이고, now()가 주는 트랜잭션 시작 시각은 beforePost보다 항상 앞이기 때문이다.
    //
    // 상한은 두지 않는다. 실패를 만들어 본 네 경우 중 상한이 잡은 것이 하나도 없었고, 단언 시점에
    // 시각을 다시 재는 상한은 테스트가 길어질수록 느슨해져 사실상 통과가 보장된다. 이 레포가 MS2-150
    // 8단계에서 지운 "절대 실패할 수 없는 단언"과 같은 것이 된다.
    //
    // parse가 형식도 함께 본다. 비ISO 패턴과 epoch 정수화 둘 다 여기서 걸리는 것을 확인했다.
    OffsetDateTime createdAt =
        OffsetDateTime.parse(jsonMapper.readTree(bodyText(created)).get("created_at").asString());
    assertThat(createdAt).isAfterOrEqualTo(beforePost);

    assertThat(list(orgId))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("아크메 주식회사");
  }

  @Test
  void 발급된_id로_이벤트를_수집할_수_있다() {
    UUID orgId = insertOrganization();
    UUID customerId = createCustomer(orgId, "아크메");

    // 이 API의 존재 이유다. 시드를 고치지 않고 만든 고객이 청구 파이프라인에 실제로 붙는지 본다.
    assertThat(
            mvc.post()
                .uri("/v1/events")
                .header("X-Organization-Id", orgId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"transaction_id":"tx-1","customer_id":"%s","event_type":"chat_completion",
                     "properties":{"token":1200},"timestamp":"2026-08-10T12:00:00+09:00"}
                    """
                        .formatted(customerId))
                .exchange())
        .hasStatusOk();
  }

  @Test
  void 이름이_비었거나_공백뿐이면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization();

    for (String invalid : new String[] {"{\"name\":\"\"}", "{\"name\":\"   \"}", "{}"}) {
      assertThat(post(orgId, invalid))
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .asString()
          .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }
    assertThat(customerCount(orgId)).isZero();
  }

  @Test
  void 이름은_255자까지_받고_256자는_거절한다() {
    UUID orgId = insertOrganization();

    assertThat(
            post(
                orgId,
                """
        {"name":"%s"}
        """
                    .formatted("가".repeat(255))))
        .hasStatus(201);

    assertThat(
            post(
                orgId,
                """
        {"name":"%s"}
        """
                    .formatted("가".repeat(256))))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .asString()
        .isEqualTo("name");
  }

  /**
   * 없는 도입사로 등록하면 500이 아니라 400이다.
   *
   * <p>헤더 값을 검증하는 곳이 없어 FK 위반이 그대로 올라온다. 500은 서버가 잘못했다는 신호라 도입사가 자기 헤더를 의심하지 않는다.
   */
  @Test
  void 등록되지_않은_도입사로_등록하면_400이다() {
    assertThat(
            post(
                UUID.randomUUID(),
                """
        {"name":"아크메"}
        """))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.UNKNOWN_ORGANIZATION);
  }

  @Test
  void 같은_이름을_두_번_등록하면_둘_다_남는다() {
    UUID orgId = insertOrganization();

    UUID first = createCustomer(orgId, "아크메");
    UUID second = createCustomer(orgId, "아크메");

    // 유니크 제약이 없어 정상 동작이다. 구별은 customer_id가 한다.
    assertThat(first).isNotEqualTo(second);
    assertThat(customerCount(orgId)).isEqualTo(2);
  }

  // --- 목록 ---

  @Test
  void 목록은_이름_오름차순이고_다른_도입사_고객은_섞이지_않는다() {
    UUID orgId = insertOrganization();
    createCustomer(orgId, "히읗");
    createCustomer(orgId, "기역");
    createCustomer(orgId, "니은");

    UUID otherOrgId = insertOrganization();
    createCustomer(otherOrgId, "남의 고객");

    assertThat(list(orgId))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("기역", "니은", "히읗");
  }

  @Test
  void 고객이_없으면_빈_배열이다() {
    assertThat(list(insertOrganization()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers")
        .asArray()
        .isEmpty();
  }

  // --- 수정 ---

  @Test
  void 이름을_고치면_200이고_목록에_반영된다() {
    UUID orgId = insertOrganization();
    MvcTestResult created =
        post(
            orgId,
            """
        {"name":"옛 이름"}
        """);
    UUID customerId =
        UUID.fromString(jsonMapper.readTree(bodyText(created)).get("customer_id").asString());
    String createdAt = jsonMapper.readTree(bodyText(created)).get("created_at").asString();

    MvcTestResult renamed =
        put(
            orgId,
            customerId,
            """
        {"name":"새 이름"}
        """);
    assertThat(renamed).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("새 이름");

    // 세 응답이 같은 created_at을 낸다 (D4가 "레코드 하나를 셋이 공유한다"고 정한 것의 채점자).
    // 등록에만 단언이 있으면 수정과 목록에서 값이 사라지거나 달라져도 전부 초록이다. insertable=false
    // 역검증에서 18건 중 1건만 죽고 목록과 수정이 통과한 것이 그 실측 증거였다.
    assertThat(renamed).bodyJson().extractingPath("$.created_at").asString().isEqualTo(createdAt);

    assertThat(list(orgId))
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("새 이름");
    assertThat(list(orgId))
        .bodyJson()
        .extractingPath("$.customers[0].created_at")
        .asString()
        .isEqualTo(createdAt);
  }

  @Test
  void 다른_도입사_고객은_고칠_수_없다() {
    UUID orgId = insertOrganization();
    UUID otherOrgId = insertOrganization();
    UUID otherCustomerId = createCustomer(otherOrgId, "남의 고객");

    assertThat(
            put(
                orgId,
                otherCustomerId,
                """
        {"name":"가로챈 이름"}
        """))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.CUSTOMER_NOT_FOUND);

    assertThat(nameOf(otherCustomerId)).isEqualTo("남의 고객");
  }

  @Test
  void 없는_고객을_고치면_404다() {
    assertThat(
            put(
                insertOrganization(),
                UUID.randomUUID(),
                """
        {"name":"아무개"}
        """))
        .hasStatus(404);
  }

  @Test
  void 경로의_id가_UUID가_아니면_500이_아니라_400이다() {
    UUID orgId = insertOrganization();

    assertThat(
            mvc.delete()
                .uri("/v1/customers/not-a-uuid")
                .header("X-Organization-Id", orgId.toString())
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
  }

  // --- 삭제 ---

  @Test
  void 이벤트가_없는_고객은_삭제되고_목록에서_빠진다() {
    UUID orgId = insertOrganization();
    UUID customerId = createCustomer(orgId, "지울 고객");

    assertThat(delete(orgId, customerId)).hasStatus(204);

    assertThat(list(orgId)).bodyJson().extractingPath("$.customers").asArray().isEmpty();
    // 행이 실제로 사라진다. 감추는 것이 아니다.
    assertThat(customerCount(orgId)).isZero();
  }

  @Test
  void 이벤트가_있는_고객을_지우면_409이고_고객은_그대로다() {
    UUID orgId = insertOrganization();
    UUID customerId = createCustomer(orgId, "이벤트 있는 고객");
    insertEvent(orgId, customerId);

    assertThat(delete(orgId, customerId))
        .hasStatus(409)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.CUSTOMER_HAS_EVENTS);

    assertThat(customerCount(orgId)).isEqualTo(1);
    assertThat(list(orgId)).bodyJson().extractingPath("$.customers").asArray().hasSize(1);
  }

  @Test
  void 같은_고객을_두_번_지우면_두_번째는_404다() {
    UUID orgId = insertOrganization();
    UUID customerId = createCustomer(orgId, "지울 고객");

    assertThat(delete(orgId, customerId)).hasStatus(204);

    // DELETE를 여러 번 불러도 같은 결과를 기대하는 쪽에서는 뜻밖일 수 있다. 행이 사라지고 나면 지운 고객과
    // 처음부터 없던 고객이 구별되지 않는다 (컨트롤러 문서에 적어 두었다).
    assertThat(delete(orgId, customerId))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.CUSTOMER_NOT_FOUND);
  }

  @Test
  void 지운_고객은_고칠_수도_없다() {
    UUID orgId = insertOrganization();
    UUID customerId = createCustomer(orgId, "지울 고객");
    delete(orgId, customerId);

    assertThat(
            put(
                orgId,
                customerId,
                """
        {"name":"되살리기 시도"}
        """))
        .hasStatus(404);
  }

  @Test
  void 다른_도입사_고객은_지울_수_없다() {
    UUID orgId = insertOrganization();
    UUID otherOrgId = insertOrganization();
    UUID otherCustomerId = createCustomer(otherOrgId, "남의 고객");

    assertThat(delete(orgId, otherCustomerId)).hasStatus(404);
    assertThat(customerCount(otherOrgId)).isEqualTo(1);
  }

  // --- 공통 ---

  @Test
  void 도입사_헤더가_없으면_네_메서드_모두_400이다() {
    assertThat(mvc.get().uri("/v1/customers").exchange()).hasStatus(400);
    assertThat(
            mvc.post()
                .uri("/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"아크메\"}")
                .exchange())
        .hasStatus(400);
    assertThat(
            mvc.put()
                .uri("/v1/customers/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"아크메\"}")
                .exchange())
        .hasStatus(400);
    assertThat(mvc.delete().uri("/v1/customers/" + UUID.randomUUID()).exchange()).hasStatus(400);
  }

  // --- 헬퍼 ---

  private MvcTestResult list(UUID organizationId) {
    return mvc.get()
        .uri("/v1/customers")
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  private MvcTestResult post(UUID organizationId, String jsonBody) {
    return mvc.post()
        .uri("/v1/customers")
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private MvcTestResult put(UUID organizationId, UUID customerId, String jsonBody) {
    return mvc.put()
        .uri("/v1/customers/" + customerId)
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private MvcTestResult delete(UUID organizationId, UUID customerId) {
    return mvc.delete()
        .uri("/v1/customers/" + customerId)
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  /** DB 서버 시각. created_at 하한 대조에 쓴다. JVM 시각을 쓰면 컨테이너와 호스트의 시계 차가 섞인다. */
  private OffsetDateTime dbNow() {
    return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
  }

  /** API로 만든다. 테스트가 검증하는 경로로 픽스처를 만들어야 발급된 id가 실제로 쓸 수 있는 값인지도 함께 확인된다. */
  private UUID createCustomer(UUID organizationId, String name) {
    MvcTestResult result =
        post(
            organizationId,
            """
        {"name":"%s"}
        """
                .formatted(name));
    return UUID.fromString(jsonMapper.readTree(bodyText(result)).get("customer_id").asString());
  }

  private String bodyText(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private void insertEvent(UUID organizationId, UUID customerId) {
    jdbc.update(
        """
        INSERT INTO usage_event
          (organization_id, transaction_id, customer_id, event_type, properties, occurred_at)
        VALUES (?, 'tx-1', ?, 'chat_completion', '{"token": 1200}', now())
        """,
        organizationId,
        customerId);
  }

  private Integer customerCount(UUID organizationId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM customer WHERE organization_id = ?", Integer.class, organizationId);
  }

  private String nameOf(UUID customerId) {
    return jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class, customerId);
  }
}
