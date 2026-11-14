package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.apikey.service.ApiKeyCredentialService;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.PrincipalKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class LocalCredentialsTest {

  private static final UUID DEMO_ORGANIZATION_ID =
      UUID.fromString("d7cee55d-8c82-4afc-b996-6749d8b26a4e");

  private static final String LIVE_KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA";

  private static final String TEST_KEY = "me_test_wiCp766QYNqhyYm4Z7Xq9wYosNvQCeVC";

  private static final String LOCAL_KEY_IDS =
      "('e0c3a216-82f4-40fe-babe-e35b49ebd24a', 'b5e65980-3d64-47fa-9b59-0ba963529bc6')";

  private static final String MEMBER_EMAIL = "member@meterengine.local";

  private static final String OPERATOR_EMAIL = "operator@meterengine.local";

  private static final List<String> TABLES =
      List.of("organization", "user_account", "organization_member", "api_key");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApiKeyCredentialService apiKeyCredentialService;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(readLocalCredentialsScript());
    TestCredentials.refreshApiKeyTable(jdbcTemplate);
  }

  @Test
  void 로컬_자격_스크립트를_다시_실행해도_행이_늘지_않는다() {
    Map<String, Long> countsBefore = rowCounts();

    jdbcTemplate.execute(readLocalCredentialsScript());

    assertThat(rowCounts()).isEqualTo(countsBefore);
  }

  @Test
  void README의_로컬_키는_데모_도입사의_같은_모드_환경_admin_키로_인증된다() {
    assertThat(apiKeyCredentialService.authenticate(LIVE_KEY))
        .hasValueSatisfying(
            credential -> assertAdminKeyOfDemoOrganization(credential.principal(), "live"));
    assertThat(apiKeyCredentialService.authenticate(TEST_KEY))
        .hasValueSatisfying(
            credential -> assertAdminKeyOfDemoOrganization(credential.principal(), "test"));
  }

  @Test
  void legacy에_있던_로컬_키도_다시_실행하면_같은_모드_환경으로_옮겨진다() {
    jdbcTemplate.update("UPDATE api_key SET environment_id = NULL WHERE id IN " + LOCAL_KEY_IDS);

    jdbcTemplate.execute(readLocalCredentialsScript());

    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT k.mode || ':' || e.mode FROM api_key k
                JOIN organization_environment e ON e.id = k.environment_id
                WHERE k.id IN %s ORDER BY k.mode
                """
                    .formatted(LOCAL_KEY_IDS),
                String.class))
        .containsExactly("live:live", "test:test");
  }

  @Test
  void README의_로컬_비밀번호가_저장된_해시와_맞는다() {
    assertThat(passwordEncoder.matches("local-member-password", passwordHashOf(MEMBER_EMAIL)))
        .isTrue();
    assertThat(passwordEncoder.matches("local-operator-password", passwordHashOf(OPERATOR_EMAIL)))
        .isTrue();
  }

  @Test
  void 로컬_member는_두_도입사에_소속되고_operator는_소속이_없다() {
    assertThat(membershipsOf(MEMBER_EMAIL)).isEqualTo(2);
    assertThat(membershipsOf(OPERATOR_EMAIL)).isZero();
  }

  @Test
  void 로컬_member는_두_도입사의_대표관리자이고_이미_있는_소속도_다시_실행하면_대표관리자가_된다() {
    assertThat(memberRolesOf(MEMBER_EMAIL)).containsExactly("owner:admin", "owner:admin");

    jdbcTemplate.update(
        """
        UPDATE organization_member SET role = 'member', scopes = '{read}', revoked_at = now()
        WHERE user_account_id = (SELECT id FROM user_account WHERE email = ?)
        """,
        MEMBER_EMAIL);
    jdbcTemplate.execute(readLocalCredentialsScript());

    assertThat(memberRolesOf(MEMBER_EMAIL)).containsExactly("owner:admin", "owner:admin");
  }

  private void assertAdminKeyOfDemoOrganization(AuthenticatedPrincipal principal, String mode) {
    assertThat(principal.kind()).isEqualTo(PrincipalKind.API_KEY);
    assertThat(principal.organizationId()).isEqualTo(DEMO_ORGANIZATION_ID);
    assertThat(principal.mode()).isEqualTo(mode);
    assertThat(principal.environmentId()).isEqualTo(environmentIdOf(mode));
    assertThat(principal.scopes()).containsExactly("admin");
  }

  private UUID environmentIdOf(String mode) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM organization_environment WHERE organization_id = ? AND mode = ?",
        UUID.class,
        DEMO_ORGANIZATION_ID,
        mode);
  }

  private String passwordHashOf(String email) {
    return jdbcTemplate.queryForObject(
        "SELECT password_hash FROM user_account WHERE email = ?", String.class, email);
  }

  private long membershipsOf(String email) {
    return jdbcTemplate.queryForObject(
        """
        SELECT count(*) FROM organization_member m
        JOIN user_account u ON u.id = m.user_account_id
        WHERE u.email = ?
        """,
        Long.class,
        email);
  }

  private List<String> memberRolesOf(String email) {
    return jdbcTemplate.queryForList(
        """
        SELECT m.role || ':' || array_to_string(m.scopes, ',') FROM organization_member m
        JOIN user_account u ON u.id = m.user_account_id
        WHERE u.email = ? AND m.revoked_at IS NULL
        ORDER BY m.created_at
        """,
        String.class,
        email);
  }

  private Map<String, Long> rowCounts() {
    return TABLES.stream().collect(Collectors.toMap(Function.identity(), this::rowCount));
  }

  private long rowCount(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  private String readLocalCredentialsScript() {
    try (InputStream in =
        new ClassPathResource("db/local/R__local_credentials.sql").getInputStream()) {
      return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("로컬 자격 스크립트를 읽지 못했다", e);
    }
  }
}
