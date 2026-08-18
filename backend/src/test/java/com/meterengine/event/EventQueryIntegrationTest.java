package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.ErrorCodes;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.event.dto.EventPageResponse;
import com.meterengine.metric.service.MetricUsageService;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이벤트 조회 API를 HTTP 계층부터 DB까지 관통해 검증한다 (MS2-131).
 *
 * <p>{@code @AutoConfigureMockMvc}를 쓰지 않고 WebApplicationContext에서 직접 만드는 이유는 {@link
 * EventIngestIntegrationTest} 참조. 컨텍스트를 공유해 Postgres 컨테이너가 한 번만 뜬다.
 *
 * <p><b>실제 Postgres여야 한다.</b> 월 귀속이 TIMESTAMPTZ 비교의 의미에 달려 있고, 동점 정렬은 DB가 실제로 어떤 순서를 내놓느냐의 문제다. 둘 다
 * 인메모리 DB로 바꾸면 통과 여부가 그 DB의 방언을 따라간다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EventQueryIntegrationTest {

  private static final String AUGUST = "2026-08";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper jsonMapper;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  // ---------------------------------------------------------------------------
  // 도입사 스코프
  // ---------------------------------------------------------------------------

  @Test
  void 다른_도입사의_이벤트는_어떤_파라미터로도_나오지_않는다() {
    UUID mine = insertOrganization("내 도입사");
    UUID myCustomer = insertCustomer(mine, "아크메");
    insertEvent(mine, "tx-mine", myCustomer, "chat_completion", 100, "2026-08-10T12:00:00+09:00");

    UUID theirs = insertOrganization("남의 도입사");
    UUID theirCustomer = insertCustomer(theirs, "베타");
    insertEvent(
        theirs, "tx-theirs", theirCustomer, "chat_completion", 999, "2026-08-10T12:00:00+09:00");

    assertThat(get(mine, "?month=" + AUGUST))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.events[*].transaction_id")
        .asArray()
        .containsExactly("tx-mine");

    // 남의 고객 ID를 알고 있어도 못 본다. 고객 판정이 organization_id와 함께 걸려 400이 된다.
    assertThat(get(mine, "?customer_id=" + theirCustomer))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.UNKNOWN_CUSTOMER_REFERENCE);
  }

  // ---------------------------------------------------------------------------
  // 정렬 (인수 조건: 같은 요청을 여러 번 보내도 순서가 같다)
  // ---------------------------------------------------------------------------

  @Test
  void occurred_at이_같아도_transaction_id로_순서가_고정된다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    String sameMoment = "2026-08-10T12:00:00+09:00";
    insertEvent(orgId, "tx-a", customerId, "chat_completion", 1, sameMoment);
    insertEvent(orgId, "tx-b", customerId, "chat_completion", 2, sameMoment);
    insertEvent(orgId, "tx-c", customerId, "chat_completion", 3, sameMoment);

    // 한 번 맞는 것으로는 부족하다. 동점 순서를 DB가 약속하지 않으므로 여러 번 불러 같은지 본다.
    for (int attempt = 0; attempt < 5; attempt++) {
      assertThat(get(orgId, "?month=" + AUGUST))
          .hasStatusOk()
          .bodyJson()
          .extractingPath("$.events[*].transaction_id")
          .asArray()
          .containsExactly("tx-c", "tx-b", "tx-a");
    }
  }

  @Test
  void 동점이_페이지_경계에_걸쳐도_중복도_누락도_없다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    String sameMoment = "2026-08-10T12:00:00+09:00";
    for (int i = 1; i <= 5; i++) {
      insertEvent(orgId, "tx-%d".formatted(i), customerId, "chat_completion", i, sameMoment);
    }

    // tiebreaker가 존재하는 이유가 바로 이 조합이다. 동점만 보거나 페이지만 보면
    // ORDER BY에서 transaction_id를 지워도 테스트가 통과한다.
    List<String> collected = new ArrayList<>();
    for (int page = 0; page < 3; page++) {
      collected.addAll(
          transactionIds(get(orgId, "?month=%s&page=%d&size=2".formatted(AUGUST, page))));
    }

    assertThat(collected).containsExactly("tx-5", "tx-4", "tx-3", "tx-2", "tx-1");
  }

  @Test
  void 최신_occurred_at이_먼저_나온다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-old", customerId, "chat_completion", 1, "2026-08-01T09:00:00+09:00");
    insertEvent(orgId, "tx-new", customerId, "chat_completion", 2, "2026-08-20T09:00:00+09:00");

    assertThat(get(orgId, "?month=" + AUGUST))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.events[*].transaction_id")
        .asArray()
        .containsExactly("tx-new", "tx-old");
  }

  // ---------------------------------------------------------------------------
  // 페이지 나누기
  // ---------------------------------------------------------------------------

  @Test
  void 전체를_size로_나눠_끝까지_받아_합치면_중복도_누락도_없다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    for (int i = 1; i <= 5; i++) {
      insertEvent(
          orgId,
          "tx-%d".formatted(i),
          customerId,
          "chat_completion",
          i,
          "2026-08-%02dT12:00:00+09:00".formatted(i));
    }

    List<String> collected = new ArrayList<>();
    for (int page = 0; page < 3; page++) {
      MvcTestResult result = get(orgId, "?month=%s&page=%d&size=2".formatted(AUGUST, page));
      assertThat(result).hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(5);
      collected.addAll(transactionIds(result));
    }

    assertThat(collected).containsExactly("tx-5", "tx-4", "tx-3", "tx-2", "tx-1");
  }

  @Test
  void 범위를_넘는_page는_오류가_아니라_빈_목록이다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", customerId, "chat_completion", 1, "2026-08-10T12:00:00+09:00");

    // 데이터가 없는 것이지 요청이 틀린 것이 아니다. total은 필터 기준 그대로 남는다.
    MvcTestResult result = get(orgId, "?month=%s&page=999".formatted(AUGUST));
    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.events").asArray().isEmpty();
    assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(1);
  }

  @Test
  void page와_size를_생략하면_0번_페이지_20줄이다() {
    UUID orgId = insertOrganization("도입사");

    assertThat(get(orgId, "?month=" + AUGUST))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.page")
        .isEqualTo(0);
    assertThat(get(orgId, "?month=" + AUGUST))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.size")
        .isEqualTo(20);
  }

  // ---------------------------------------------------------------------------
  // 필터
  // ---------------------------------------------------------------------------

  @Test
  void 세_필터가_각각_걸리고_함께_주면_AND다() {
    UUID orgId = insertOrganization("도입사");
    UUID acme = insertCustomer(orgId, "아크메");
    UUID beta = insertCustomer(orgId, "베타");
    insertEvent(orgId, "tx-acme-chat", acme, "chat_completion", 1, "2026-08-10T12:00:00+09:00");
    insertEvent(orgId, "tx-acme-embed", acme, "embedding", 2, "2026-08-10T12:00:00+09:00");
    insertEvent(orgId, "tx-beta-chat", beta, "chat_completion", 3, "2026-08-10T12:00:00+09:00");
    insertEvent(orgId, "tx-acme-july", acme, "chat_completion", 4, "2026-07-10T12:00:00+09:00");

    assertThat(transactionIds(get(orgId, "?month=%s&customer_id=%s".formatted(AUGUST, acme))))
        .containsExactlyInAnyOrder("tx-acme-chat", "tx-acme-embed");
    assertThat(transactionIds(get(orgId, "?month=%s&event_type=embedding".formatted(AUGUST))))
        .containsExactly("tx-acme-embed");
    assertThat(transactionIds(get(orgId, "?month=2026-07"))).containsExactly("tx-acme-july");

    // 셋을 함께 주면 교집합이다.
    assertThat(
            transactionIds(
                get(
                    orgId,
                    "?month=%s&customer_id=%s&event_type=chat_completion".formatted(AUGUST, acme))))
        .containsExactly("tx-acme-chat");
  }

  @Test
  void total은_필터를_적용한_뒤의_건수다() {
    UUID orgId = insertOrganization("도입사");
    UUID acme = insertCustomer(orgId, "아크메");
    UUID beta = insertCustomer(orgId, "베타");
    insertEvent(orgId, "tx-1", acme, "chat_completion", 1, "2026-08-10T12:00:00+09:00");
    insertEvent(orgId, "tx-2", beta, "chat_completion", 2, "2026-08-10T12:00:00+09:00");

    assertThat(get(orgId, "?month=" + AUGUST))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total")
        .isEqualTo(2);
    assertThat(get(orgId, "?month=%s&customer_id=%s".formatted(AUGUST, acme)))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total")
        .isEqualTo(1);
  }

  @Test
  void 미터에_없는_event_type도_저장돼_있으면_그대로_조회된다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    // usage_event.event_type은 미터와 FK 없는 논리 매칭이라 미등록 값도 저장된다 (V1 주석).
    // 로그는 원문 보존이므로 집계에 안 잡히는 값도 화면에는 보여야 한다.
    insertEvent(orgId, "tx-unknown", customerId, "정체불명", 1, "2026-08-10T12:00:00+09:00");

    assertThat(transactionIds(get(orgId, "?month=%s&event_type=%s".formatted(AUGUST, "정체불명"))))
        .containsExactly("tx-unknown");
  }

  // ---------------------------------------------------------------------------
  // 월 경계 (인수 조건: 사용량 집계와 같은 판정)
  // ---------------------------------------------------------------------------

  @Test
  void 팔월_마지막_순간은_팔월이고_구월_첫_순간은_구월이다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-aug", customerId, "chat_completion", 1, "2026-08-31T23:59:59+09:00");
    insertEvent(orgId, "tx-sep", customerId, "chat_completion", 2, "2026-09-01T00:00:00+09:00");

    assertThat(transactionIds(get(orgId, "?month=" + AUGUST))).containsExactly("tx-aug");
    assertThat(transactionIds(get(orgId, "?month=2026-09"))).containsExactly("tx-sep");
  }

  @Test
  void 같은_순간을_UTC로_보낸_이벤트도_같은_달에_귀속된다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    // 2026-08-31T14:59:59Z == 2026-08-31T23:59:59+09:00. 표기가 달라도 같은 순간이다.
    insertEvent(orgId, "tx-utc", customerId, "chat_completion", 1, "2026-08-31T14:59:59Z");

    assertThat(transactionIds(get(orgId, "?month=" + AUGUST))).containsExactly("tx-utc");
  }

  @Test
  void month를_생략하면_이번_달이고_응답이_어느_달인지_알려준다() {
    UUID orgId = insertOrganization("도입사");
    String thisMonth = MetricUsageService.currentMonth().toString();

    // 생략했을 때 서버가 어느 달을 골랐는지 응답만 보고 알 수 있어야 한다.
    assertThat(get(orgId, ""))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.month")
        .isEqualTo(thisMonth);
  }

  // ---------------------------------------------------------------------------
  // 사용량 집계와의 대조 (인수 조건: 두 화면 숫자가 같아야 한다)
  // ---------------------------------------------------------------------------

  @Test
  void 로그의_숫자형_token_합이_사용량_집계_값과_같다() {
    UUID orgId = insertOrganization("도입사");
    insertTokenMetric(orgId);
    UUID customerId = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", customerId, "chat_completion", 300, "2026-08-05T10:00:00+09:00");
    insertEvent(orgId, "tx-2", customerId, "chat_completion", 200, "2026-08-20T10:00:00+09:00");
    // 집계는 jsonb_typeof가 number가 아닌 값을 뺀다. 로그는 원문을 다 보여주므로 합을 낼 때
    // 같은 기준으로 세야 두 화면이 맞는다.
    insertEventWithProperties(
        orgId,
        "tx-text",
        customerId,
        "chat_completion",
        "{\"token\": \"많이\"}",
        "2026-08-21T10:00:00+09:00");

    // 이 불변식을 지키는 것은 두 파일에 따로 있는 기간 계산이 같다는 것뿐이다. 갈리면
    // 청구 근거 화면과 청구 금액이 어긋나는데, 이 테스트가 없으면 감지할 장치가 없다.
    assertThat(
            numericTokenSum(get(orgId, "?month=%s&customer_id=%s".formatted(AUGUST, customerId))))
        .isEqualByComparingTo(aggregatedQuantity(orgId, customerId));
  }

  // ---------------------------------------------------------------------------
  // 빈 목록과 없는 고객 (인수 조건: 이 둘이 갈리는 것이 핵심)
  // ---------------------------------------------------------------------------

  @Test
  void 이벤트가_0건인_등록_고객은_200_빈_목록이다() {
    UUID orgId = insertOrganization("도입사");
    UUID quiet = insertCustomer(orgId, "조용한 고객");

    MvcTestResult result = get(orgId, "?month=%s&customer_id=%s".formatted(AUGUST, quiet));

    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(0);
    assertThat(result).bodyJson().extractingPath("$.events").asArray().isEmpty();
  }

  @Test
  void 미등록_고객으로_필터하면_400이고_code가_unknown_customer_reference다() {
    UUID orgId = insertOrganization("도입사");

    assertThat(get(orgId, "?customer_id=" + UUID.randomUUID()))
        .hasStatus(400)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.UNKNOWN_CUSTOMER_REFERENCE);
  }

  // ---------------------------------------------------------------------------
  // 잘못된 파라미터 (인수 조건: 400이고 code=validation_error, 500이 아니다)
  // ---------------------------------------------------------------------------

  @Test
  void size가_범위_밖이면_500이_아니라_400이다() {
    UUID orgId = insertOrganization("도입사");

    // 조용히 100으로 깎으면 FE가 왜 요청한 만큼 안 오는지 모른다. 못 주면 못 준다고 말한다.
    assertThat(get(orgId, "?size=101"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    assertThat(get(orgId, "?size=0"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
  }

  @Test
  void 허용_경계인_size_1과_100은_통과한다() {
    UUID orgId = insertOrganization("도입사");

    // 거절만 검증하면 @Min(2) 같은 오타를 못 잡는다. 통과해야 하는 쪽도 본다.
    assertThat(get(orgId, "?size=1")).hasStatusOk();
    assertThat(get(orgId, "?size=100")).hasStatusOk();
    assertThat(get(orgId, "?page=0")).hasStatusOk();
  }

  @Test
  void 헤더가_없으면_400이고_code가_붙는다() {
    // 가장 흔한 400이라 오히려 빠뜨리기 쉽다. FE가 code로 문구를 고르므로 여기만 비면 안 된다.
    assertThat(mvc.get().uri("/v1/events").exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
  }

  @Test
  void event_type을_빈_값으로_보내면_필터가_걸리지_않는다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", customerId, "chat_completion", 1, "2026-08-10T12:00:00+09:00");

    // FE가 필터를 비우며 빈 값을 그대로 붙이는 구현이 흔하다. 그때 event_type = '' 로 걸리면
    // 데이터가 있는데도 화면이 빈다. customer_id와 month는 스프링이 알아서 null로 바꾼다.
    assertThat(transactionIds(get(orgId, "?month=%s&event_type=".formatted(AUGUST))))
        .containsExactly("tx-1");
  }

  @Test
  void 음수_page는_500이_아니라_400이다() {
    UUID orgId = insertOrganization("도입사");

    // 방어가 없으면 OFFSET이 음수가 되어 DB 에러로 500이 나간다.
    assertThat(get(orgId, "?page=-1"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
  }

  @Test
  void UUID가_아닌_customerId와_형식이_틀린_month도_code가_붙는다() {
    UUID orgId = insertOrganization("도입사");

    // 프레임워크도 이 둘을 400으로 내주지만 code가 없다. FE가 code로 문구를 고르므로 일부만 빠지면 안 된다.
    assertThat(get(orgId, "?customer_id=abc"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    assertThat(get(orgId, "?month=2026-13"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
  }

  // ---------------------------------------------------------------------------
  // 응답 항목
  // ---------------------------------------------------------------------------

  @Test
  void 응답에_고객_이름이_함께_실린다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메 주식회사");
    insertEvent(orgId, "tx-1", customerId, "chat_completion", 100, "2026-08-10T12:00:00+09:00");

    // 이벤트 행에는 customer_id뿐이라 조인하지 않으면 화면이 UUID만 보여주게 된다.
    assertThat(get(orgId, "?month=" + AUGUST))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.events[0].customer_name")
        .asString()
        .isEqualTo("아크메 주식회사");
  }

  @Test
  void properties는_소수_스무자리도_자릿수가_그대로_돌아온다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    // 저장은 통합 테스트가 이미 지키지만 조회 왕복은 아무도 안 지킨다. 파싱을 끼우면 여기서 잘린다.
    insertEventWithProperties(
        orgId,
        "tx-1",
        customerId,
        "chat_completion",
        "{\"cost\": 0.1234567890123456789}",
        "2026-08-10T12:00:00+09:00");

    // 파싱된 값이 아니라 응답 원문을 본다. JSONPath로 꺼내면 그 경로에서 한 번 더 파싱돼,
    // 정작 검증하려는 "네트워크로 나가는 글자"가 무엇인지 흐려진다.
    MvcTestResult result = get(orgId, "?month=" + AUGUST);
    assertThat(result).hasStatusOk();
    assertThat(bodyText(result)).contains("0.1234567890123456789");
  }

  @Test
  void properties는_키를_가리지_않고_원문_그대로_나간다() {
    UUID orgId = insertOrganization("도입사");
    UUID customerId = insertCustomer(orgId, "아크메");
    // 화면이 model=... token=... 처럼 전부 펼쳐 보여주므로 서버가 키를 골라서는 안 된다.
    insertEventWithProperties(
        orgId,
        "tx-1",
        customerId,
        "chat_completion",
        "{\"model\": \"gpt-4o\", \"token\": 2040, \"cached\": true}",
        "2026-08-10T12:00:00+09:00");

    assertThat(get(orgId, "?month=" + AUGUST))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.events[0].properties")
        .asMap()
        .containsKeys("model", "token", "cached");
  }

  // ---------------------------------------------------------------------------
  // 헬퍼
  // ---------------------------------------------------------------------------

  private MvcTestResult get(UUID organizationId, String query) {
    return mvc.get()
        .uri("/v1/events" + query)
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  /**
   * 응답에서 transaction_id만 순서대로 뽑는다. 정렬과 필터 검증이 대부분 이 목록 비교로 끝난다.
   *
   * <p>{@link EventPageResponse}로 역직렬화하지 않는다. properties가 {@code @JsonRawValue}라 그 DTO는 쓰기 전용이고,
   * 읽으려 들면 객체를 String에 넣지 못해 터진다. 트리로 읽으면 그 필드를 건드리지 않는다.
   */
  private List<String> transactionIds(MvcTestResult result) {
    List<String> ids = new ArrayList<>();
    jsonMapper
        .readTree(bodyText(result))
        .get("events")
        .forEach(event -> ids.add(event.get("transaction_id").asString()));
    return ids;
  }

  /** 이벤트 로그 응답에서 숫자형 token만 더한다. 집계가 숫자 아닌 값을 빼므로 같은 기준으로 센다. */
  private BigDecimal numericTokenSum(MvcTestResult result) {
    BigDecimal sum = BigDecimal.ZERO;
    for (JsonNode event : jsonMapper.readTree(bodyText(result)).get("events")) {
      JsonNode token = event.get("properties").get("token");
      if (token != null && token.isNumber()) {
        sum = sum.add(token.decimalValue());
      }
    }
    return sum;
  }

  /** 같은 도입사, 같은 고객을 사용량 조회 API로 물어본 집계 수량. */
  private BigDecimal aggregatedQuantity(UUID organizationId, UUID customerId) {
    MvcTestResult result =
        mvc.get()
            .uri("/v1/usage?month=" + AUGUST)
            .header("X-Organization-Id", organizationId.toString())
            .exchange();
    for (JsonNode customer :
        jsonMapper.readTree(bodyText(result)).get("metrics").get(0).get("customers")) {
      if (customerId.toString().equals(customer.get("customer_id").asString())) {
        return customer.get("quantity").decimalValue();
      }
    }
    throw new IllegalStateException("집계 응답에 고객이 없다: " + customerId);
  }

  /** 시드와 같은 모양의 미터(chat_completion의 token을 SUM). 집계가 이 미터로 합을 낸다. */
  private void insertTokenMetric(UUID organizationId) {
    jdbc.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property, unit_price)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'SUM', 'token', 0.5)
        """,
        organizationId);
  }

  private String bodyText(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }

  private UUID insertOrganization(String name) {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES (?) RETURNING id", UUID.class, name);
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbc.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name);
  }

  private void insertEvent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String eventType,
      int token,
      String occurredAt) {
    insertEventWithProperties(
        organizationId,
        transactionId,
        customerId,
        eventType,
        "{\"token\": %d}".formatted(token),
        occurredAt);
  }

  private void insertEventWithProperties(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String eventType,
      String propertiesJson,
      String occurredAt) {
    jdbc.update(
        """
        INSERT INTO usage_event
          (organization_id, transaction_id, customer_id, event_type, properties, occurred_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?)
        """,
        organizationId,
        transactionId,
        customerId,
        eventType,
        propertiesJson,
        OffsetDateTime.parse(occurredAt));
  }
}
