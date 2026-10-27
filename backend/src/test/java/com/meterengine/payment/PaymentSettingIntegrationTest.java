package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PaymentSettingIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 설정이_없으면_test_모드이고_updated_at은_null이다() {
    UUID organizationId = insertOrganization();

    assertThat(getPaymentSetting(organizationId))
        .hasStatus(200)
        .bodyJson()
        .isStrictlyEqualTo("{\"mode\": \"test\", \"updated_at\": null}");
  }

  @Test
  void live_키_없이_live로_바꾸면_409이고_설정은_test로_남는다() {
    UUID organizationId = insertOrganization();
    insertCredential(organizationId, "test");

    assertError(
        updatePaymentSetting(organizationId, "live"),
        409,
        ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);

    assertThat(countSettings(organizationId)).isZero();
    assertThat(getPaymentSetting(organizationId))
        .bodyJson()
        .extractingPath("$.mode")
        .isEqualTo("test");
  }

  @Test
  void live_키를_저장한_뒤에는_live로_바뀌고_조회에도_live가_나온다() {
    UUID organizationId = insertOrganization();
    insertCredential(organizationId, "live");

    MvcTestResult updated = updatePaymentSetting(organizationId, "live");

    assertThat(updated).hasStatus(200);
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT * FROM payment_setting WHERE organization_id = ?", organizationId);
    assertThat(row).containsEntry("mode", "live");
    String expected =
        "{\"mode\": \"live\", \"updated_at\": \"%s\"}".formatted(kst(row.get("updated_at")));
    assertThat(updated).bodyJson().isStrictlyEqualTo(expected);
    assertThat(getPaymentSetting(organizationId)).bodyJson().isStrictlyEqualTo(expected);
  }

  @Test
  void live에서_test로_되돌리면_행_하나가_바뀐다() {
    UUID organizationId = insertOrganization();
    insertCredential(organizationId, "live");
    assertThat(updatePaymentSetting(organizationId, "live")).hasStatus(200);

    assertThat(updatePaymentSetting(organizationId, "test"))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.mode")
        .isEqualTo("test");

    assertThat(countSettings(organizationId)).isEqualTo(1);
  }

  @Test
  void 모드가_test나_live가_아니거나_비어_있으면_400_validation_error다() {
    UUID organizationId = insertOrganization();

    assertError(updatePaymentSetting(organizationId, "prod"), 400, ErrorCode.VALIDATION_ERROR);
    assertError(updatePaymentSetting(organizationId, ""), 400, ErrorCode.VALIDATION_ERROR);
    assertThat(countSettings(organizationId)).isZero();
  }

  private MvcTestResult getPaymentSetting(UUID organizationId) {
    return withOrganization(mvc.get().uri("/v1/payment-setting"), organizationId).exchange();
  }

  private MvcTestResult updatePaymentSetting(UUID organizationId, String mode) {
    return withOrganization(mvc.put().uri("/v1/payment-setting"), organizationId)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"mode\": \"%s\"}".formatted(mode))
        .exchange();
  }

  private MockMvcTester.MockMvcRequestBuilder withOrganization(
      MockMvcTester.MockMvcRequestBuilder request, UUID organizationId) {
    return request.header(
        "Authorization", TestCredentials.issueMemberSession(jdbcTemplate, organizationId));
  }

  private void assertError(MvcTestResult result, int status, ErrorCode errorCode) {
    assertThat(result)
        .hasStatus(status)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(errorCode.getCode());
  }

  private void insertCredential(UUID organizationId, String mode) {
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId,
            mode,
            mode + "_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq",
            mode + "_sk_zXLkKEypNArWmo50nX3lmeaxYG5R",
            OffsetDateTime.parse("2026-10-01T00:00:00Z")));
  }

  private int countSettings(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM payment_setting WHERE organization_id = ?",
        Integer.class,
        organizationId);
  }

  private static String kst(Object timestamp) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        ((Timestamp) timestamp).toInstant().atOffset(ZoneOffset.ofHours(9)));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }
}
