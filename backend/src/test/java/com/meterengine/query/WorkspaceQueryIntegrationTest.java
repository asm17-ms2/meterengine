package com.meterengine.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.query.repository.WorkspaceQueryRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class WorkspaceQueryIntegrationTest {
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  @Autowired private WorkspaceQueryRepository workspaceQueryRepository;
  @MockitoSpyBean private InvoiceRepository invoiceRepository;
  private MockMvcTester mvc;
  private UUID organizationId;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbc, organizationId, "read");
  }

  @Test
  void 지원하지_않는_검색_조건은_거부한다() {
    for (String path :
        List.of(
            "/customers/search?typo=value",
            "/customers/search?billable_metric_code=anything",
            "/billable-metrics/search?customer_id=" + UUID.randomUUID(),
            "/usage/search?external_id=anything",
            "/invoices/draft/search?mode=test",
            "/customers/options?month=2026-10",
            "/customers/export?min_amount=1",
            "/usage/months?q=anything",
            "/invoices/months?status=paid",
            "/billable-metric-prices/months?month=2026-10")) {
      assertThat(get("/v1" + path)).as(path).hasStatus(400);
    }
  }

  @Test
  void 고객이_없어도_사용량_보관_상태_조건을_검증한다() {
    assertThat(get("/v1/usage/search?status=unknown")).hasStatus(400);
  }

  @Test
  void 고객_검색은_페이지와_무관한_건수와_조직_격리를_유지한다() {
    customer("가나다", "acme-1", false);
    customer("나라마", "acme-2", false);
    customer("보관 고객", "archive-1", true);
    UUID other = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbc, other);
    jdbc.update("INSERT INTO customer (organization_id,name) VALUES (?, '다른 도입사')", other);
    JsonNode first = data(get("/v1/customers/search?q=acme&size=1"));
    assertThat(first.get("total").asInt()).isEqualTo(2);
    assertThat(first.get("total_unfiltered").asInt()).isEqualTo(3);
    assertThat(first.get("items").size()).isEqualTo(1);
    assertThat(first.get("items").get(0).get("name").asString()).isEqualTo("가나다");
    JsonNode second = data(get("/v1/customers/search?q=acme&size=1&page=2"));
    assertThat(second.get("items").get(0).get("name").asString()).isEqualTo("나라마");
    JsonNode archived = data(get("/v1/customers/search?status=archived"));
    assertThat(archived.get("total").asInt()).isEqualTo(1);
    assertThat(archived.get("items").get(0).get("external_id").asString()).isEqualTo("archive-1");
    assertThat(data(get("/v1/customers/options?status=all")).get("items").size()).isEqualTo(3);
  }

  @Test
  void 검색은_와일드카드를_문자로_처리하고_SQL_정렬문을_거부한다() {
    customer("100%_테스트", "literal", false);
    customer("100_다른 고객", "other", false);
    assertThat(
            data(mvc.get()
                    .uri("/v1/customers/search")
                    .param("q", "%_")
                    .header(AUTHORIZATION, authorization)
                    .exchange())
                .get("total")
                .asInt())
        .isEqualTo(1);
    assertThat(get("/v1/customers/search?sort=name%20desc%3Bselect%201")).hasStatus(400);
    assertThat(get("/v1/customers/search?page=0")).hasStatus(400);
    assertThat(get("/v1/customers/search?size=201")).hasStatus(400);
    assertThat(get("/v1/customers/search?status=unknown")).hasStatus(400);
  }

  @Test
  void 미터와_가격_검색은_이름과_페이지_전체의_누락_단가_수를_반환한다() {
    metric("alpha", "가 미터", "type-alpha");
    metric("beta", "나 미터", "type-beta");
    metric("gamma", "다 미터", "type-gamma");
    rate("alpha", "2026-01", 1, null);
    rate("gamma", "2026-01", 2, null);
    JsonNode metrics = data(get("/v1/billable-metrics/search?q=미터&size=1&page=2&sort=name"));
    assertThat(metrics.get("total").asInt()).isEqualTo(3);
    assertThat(metrics.get("items").get(0).get("code").asString()).isEqualTo("beta");
    JsonNode prices =
        data(get("/v1/billable-metric-prices/search?month=2026-08&size=1&sort=rate_count"));
    assertThat(prices.get("total").asInt()).isEqualTo(3);
    assertThat(prices.get("summary").get("no_rate_count").asInt()).isEqualTo(1);
    assertThat(prices.get("items").get(0).get("billable_metric_name").asString()).isEqualTo("나 미터");
    assertThat(prices.get("items").get(0).get("rate_count").asInt()).isZero();
    assertThat(
            data(get("/v1/billable-metrics/options?q=가"))
                .get("items")
                .get(0)
                .get("value")
                .asString())
        .isEqualTo("alpha");
  }

  @Test
  void 보관한_최신_단가_버전은_이전_단가를_되살리지_않는다() {
    metric("alpha", "대상", "type-alpha");
    rate("alpha", "2026-01", 1, null);
    rate("alpha", "2026-02", 2, "2026-03");
    JsonNode prices = data(get("/v1/billable-metric-prices/search?month=2026-08"));
    assertThat(prices.get("items").get(0).get("price_rates").size()).isZero();
    assertThat(prices.get("summary").get("no_rate_count").asInt()).isEqualTo(1);
    assertThat(data(get("/v1/billable-metric-prices/months")).get("months").size()).isEqualTo(2);
  }

  @Test
  void 기한부_단가를_보관하면_원래_종료월부터_이전_단가를_다시_조회한다() {
    metric("alpha", "대상", "type-alpha");
    rate("alpha", "2026-01", 1, null);
    rate("alpha", "2026-02", 2, "2026-03");
    jdbc.update(
        "UPDATE price_rate SET effective_until='2026-04' WHERE organization_id=? AND billable_metric_code='alpha' AND effective_from='2026-02'",
        organizationId);

    JsonNode archived = data(get("/v1/billable-metric-prices/search?month=2026-03"));
    assertThat(archived.get("items").get(0).get("price_rates").size()).isZero();
    assertThat(archived.get("items").get(0).get("rate_count").asInt()).isZero();
    assertThat(archived.get("summary").get("no_rate_count").asInt()).isEqualTo(1);

    JsonNode expired = data(get("/v1/billable-metric-prices/search?month=2026-04"));
    JsonNode item = expired.get("items").get(0);
    assertThat(item.get("price_rates").size()).isEqualTo(1);
    assertThat(item.get("price_rates").get(0).get("unit_price").asInt()).isEqualTo(1);
    assertThat(item.get("price_rates").get(0).get("effective_from").asString())
        .isEqualTo("2026-01");
    assertThat(item.get("rate_count").asInt()).isEqualTo(1);
    assertThat(expired.get("summary").get("no_rate_count").asInt()).isZero();
  }

  @Test
  void 사용량은_고객별로_페이지를_나누고_모든_미터를_함께_반환한다() {
    UUID first = customer("가 고객", "first", false);
    UUID second = customer("나 고객", "second", false);
    metric("alpha", "첫 미터", "type-alpha");
    metric("beta", "둘째 미터", "type-beta");
    event(first, "type-alpha", 10);
    event(first, "type-beta", 20);
    event(second, "type-alpha", 30);
    JsonNode result = data(get("/v1/usage/search?month=2026-08&size=1"));
    assertThat(result.get("total").asInt()).isEqualTo(2);
    assertThat(result.get("items").size()).isEqualTo(1);
    assertThat(result.get("items").get(0).get("metrics").size()).isEqualTo(2);
    assertThat(result.get("summary").get("nonzero_customer_count").asInt()).isEqualTo(2);
    assertThat(result.get("summary").get("line_count").asInt()).isEqualTo(4);
    assertThat(
            data(get("/v1/usage/search?month=2026-08&page=2&size=1"))
                .get("summary")
                .get("line_count")
                .asInt())
        .isEqualTo(4);
    assertThat(
            data(get("/v1/usage/search?month=2026-08&customer_id=" + second))
                .get("summary")
                .get("line_count")
                .asInt())
        .isEqualTo(2);
    JsonNode selected =
        data(
            get(
                "/v1/usage/search?month=2026-08&customer_id="
                    + second
                    + "&billable_metric_code=alpha"));
    assertThat(selected.get("total").asInt()).isEqualTo(1);
    assertThat(selected.get("summary").get("line_count").asInt()).isEqualTo(1);
    assertThat(
            selected
                .get("items")
                .get(0)
                .get("metrics")
                .get(0)
                .get("customers")
                .get(0)
                .get("quantity")
                .asInt())
        .isEqualTo(30);
    assertThat(data(get("/v1/usage/months")).get("months").get(0).asString()).isEqualTo("2026-08");
  }

  @Test
  void 사용량_월_목록은_네_자리_연도로_열_수_없는_달을_뺀다() {
    UUID customer = customer("가 고객", "first", false);
    event(customer, "type-alpha", 10);
    jdbc.update(
        "INSERT INTO event (organization_id,transaction_id,customer_id,type,properties,occurred_at) VALUES (?,?,?,'type-alpha','{}',cast(? AS timestamptz))",
        organizationId,
        UUID.randomUUID().toString(),
        customer,
        "55835-03-10T00:00:00Z");
    assertThat(data(get("/v1/usage/months")).get("months").size()).isEqualTo(1);
    assertThat(data(get("/v1/usage/months")).get("months").get(0).asString()).isEqualTo("2026-08");
  }

  @Test
  void 모르는_자원의_월_목록은_이벤트_월로_대신하지_않고_거부한다() {
    assertThatThrownBy(() -> workspaceQueryRepository.months(organizationId, "prices"))
        .hasMessage("Unknown months resource");
  }

  @Test
  void 청구_예정액_요약은_페이지와_무관하고_월_전체_확정_범위를_표시한다() {
    UUID first = customer("가 고객", "first", false);
    UUID second = customer("나 고객", "second", false);
    metric("alpha", "첫 미터", "type-alpha");
    rate("alpha", "2026-01", 10, null);
    event(first, "type-alpha", 10);
    event(second, "type-alpha", 20);
    JsonNode page = data(get("/v1/invoices/draft/search?month=2026-08&size=1"));
    assertThat(page.get("items").size()).isEqualTo(1);
    assertThat(page.get("summary").get("amount").asInt()).isEqualTo(300);
    JsonNode selected = data(get("/v1/invoices/draft/search?month=2026-08&customer_id=" + first));
    assertThat(selected.get("summary").get("amount").asInt()).isEqualTo(100);
    assertThat(selected.get("summary").get("month_amount").asInt()).isEqualTo(300);
    assertThat(selected.get("summary").get("finalization_scope").asString()).isEqualTo("month");
  }

  @Test
  void 인보이스는_확정_시각과_금액으로_검색하고_전체_결제_요약을_반환한다() {
    UUID first = customer("가 고객", "first", false);
    UUID second = customer("나 고객", "second", false);
    UUID invoice = invoice(first, "가 고객", 100, "2026-08-05T23:59:59+09:00");
    UUID secondInvoice = invoice(second, "나 고객", 200, "2026-08-06T00:00:00+09:00");
    JsonNode page = data(get("/v1/invoices/search?size=1"));
    assertThat(page.get("total").asInt()).isEqualTo(2);
    assertThat(page.get("summary").get("total_amount").asInt()).isEqualTo(330);
    JsonNode day = data(get("/v1/invoices/search?from=2026-08-05&to=2026-08-05"));
    assertThat(day.get("total").asInt()).isEqualTo(1);
    assertThat(day.get("items").get(0).get("invoice").get("id").asString())
        .isEqualTo(invoice.toString());
    JsonNode totalRange = data(get("/v1/invoices/search?min_amount=210&max_amount=230"));
    assertThat(totalRange.get("total").asInt()).isEqualTo(1);
    assertThat(totalRange.get("items").get(0).get("invoice").get("id").asString())
        .isEqualTo(secondInvoice.toString());
    assertThat(get("/v1/invoices/search?status=bogus")).hasStatus(400);
    assertThat(get("/v1/invoices/search?min_amount=300&max_amount=200")).hasStatus(400);
    assertThat(data(get("/v1/invoices/months")).get("months").get(0).asString())
        .isEqualTo("2026-08");
  }

  @Test
  void 인보이스_검색은_고객과_월_조건을_조회에_넘긴다() {
    UUID first = customer("가 고객", "first", false);
    invoice(first, "가 고객", 100, "2026-08-05T23:59:59+09:00");
    assertThat(
            data(get("/v1/invoices/search?month=2026-08&customer_id=" + first))
                .get("total")
                .asInt())
        .isEqualTo(1);
    verify(invoiceRepository).findNewestFirst(organizationId, first, "2026-08");
  }

  @Test
  void 처리_중인_결제가_있으면_미수금을_미확정으로_표시한다() {
    UUID customer = customer("결제 확인 중", "pending", false);
    UUID invoice = invoice(customer, "결제 확인 중", 100, "2026-08-05T12:00:00+09:00");
    jdbc.update(
        "INSERT INTO payment_attempt (id,organization_id,customer_id,invoice_id,amount,order_name,status,requested_at,mode) VALUES (gen_random_uuid(),?,?,?,?, 'test', 'pending',now(),'test')",
        organizationId,
        customer,
        invoice,
        110);
    JsonNode result = data(get("/v1/invoices/search?status=pending&mode=test"));
    assertThat(result.get("total").asInt()).isEqualTo(1);
    assertThat(result.get("summary").get("has_pending").asBoolean()).isTrue();
    assertThat(result.get("summary").get("outstanding_amount").isNull()).isTrue();
  }

  @Test
  void CSV는_검색_결과_전체를_내보내고_문자열_수식을_차단한다() {
    customer("=SUM(1,2)", "formula", false);
    customer("일반 고객", "normal", false);
    MvcTestResult exported = get("/v1/customers/export?status=all&size=1");
    assertThat(exported).hasStatusOk();
    String csv = new String(exported.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    assertThat(csv).startsWith("\ufeff").contains("'=SUM(1,2)").contains("일반 고객");
    assertThat(csv.split("\r\n")).hasSize(3);
    assertThat(exported.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(exported.getResponse().getHeader("Content-Disposition")).contains("customers.csv");
  }

  @Test
  void 모든_조회_API는_조회_키를_허용하고_수집_전용_키를_거부한다() {
    String ingest = TestCredentials.issueApiKey(jdbc, organizationId, "ingest");
    for (String path :
        List.of(
            "/customers/search",
            "/billable-metrics/search",
            "/billable-metric-prices/search",
            "/usage/search",
            "/invoices/draft/search",
            "/invoices/search",
            "/customers/options",
            "/billable-metrics/options",
            "/usage/months",
            "/invoices/months",
            "/billable-metric-prices/months",
            "/customers/export",
            "/billable-metrics/export",
            "/billable-metric-prices/export",
            "/usage/export",
            "/invoices/draft/export",
            "/invoices/export")) {
      assertThat(get("/v1" + path)).as(path).hasStatusOk();
      assertThat(mvc.get().uri("/v1" + path).header(AUTHORIZATION, ingest).exchange())
          .as(path)
          .hasStatus(403);
    }
  }

  private UUID customer(String name, String external, boolean archived) {
    return jdbc.queryForObject(
        "INSERT INTO customer (organization_id,name,external_id,archived_at) VALUES (?,?,?, CASE WHEN ? THEN now() ELSE NULL END) RETURNING id",
        UUID.class,
        organizationId,
        name,
        external,
        archived);
  }

  private void metric(String code, String name, String type) {
    jdbc.update(
        "INSERT INTO billable_metric (organization_id,code,name,event_type,aggregation,target_property,dimension_properties) VALUES (?,?,?,?,'sum','tokens','{}')",
        organizationId,
        code,
        name,
        type);
  }

  private void rate(String metric, String from, long price, String archivedFrom) {
    jdbc.update(
        "INSERT INTO price_rate (organization_id,billable_metric_code,dimension_values,unit_price,effective_from,archived_at,archived_from) VALUES (?,?,'{}',?,?,CASE WHEN CAST(? AS VARCHAR) IS NOT NULL THEN now() ELSE NULL END,?)",
        organizationId,
        metric,
        price,
        from,
        archivedFrom,
        archivedFrom);
  }

  private void event(UUID customer, String type, int quantity) {
    jdbc.update(
        "INSERT INTO event (organization_id,transaction_id,customer_id,type,properties,occurred_at) VALUES (?,?,?,?,cast(? AS jsonb),cast(? AS timestamptz))",
        organizationId,
        UUID.randomUUID().toString(),
        customer,
        type,
        "{\"tokens\":" + quantity + "}",
        "2026-08-10T12:00:00+09:00");
  }

  private UUID invoice(UUID customer, String name, long supply, String finalized) {
    return jdbc.queryForObject(
        "INSERT INTO invoice (organization_id,customer_id,customer_name,period,supply_amount,tax_amount,total_amount,finalized_at) VALUES (?,?,?,'2026-08',?,?,?,cast(? AS timestamptz)) RETURNING id",
        UUID.class,
        organizationId,
        customer,
        name,
        supply,
        supply / 10,
        supply + supply / 10,
        finalized);
  }

  private MvcTestResult get(String path) {
    return mvc.get().uri(path).header(AUTHORIZATION, authorization).exchange();
  }

  private JsonNode data(MvcTestResult response) {
    assertThat(response).hasStatusOk();
    return json.readTree(
        new String(response.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
  }
}
