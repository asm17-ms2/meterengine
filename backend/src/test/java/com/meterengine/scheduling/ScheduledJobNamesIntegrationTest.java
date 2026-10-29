package com.meterengine.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.scheduling.service.ScheduledJobRunService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {"meterengine.scheduling.enabled=true", "meterengine.webhook.enabled=true"})
class ScheduledJobNamesIntegrationTest {
  private static final Path ALERTING =
      Path.of("..", "deploy", "grafana", "provisioning", "alerting", "alerting.yml");
  private static final Pattern JOB_NAME_MATCHER = Pattern.compile("job_name=~\"([a-z_|]+)\"");

  @MockitoBean private ScheduledJobRunService scheduledJobRunService;
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 등록한_예약_작업과_운영_현황과_경보가_같은_작업_이름을_쓴다() throws Exception {
    ArgumentCaptor<String> registered = ArgumentCaptor.forClass(String.class);
    verify(scheduledJobRunService, atLeastOnce()).register(registered.capture());
    Set<String> registeredNames = new HashSet<>(registered.getAllValues());

    String operations =
        new String(
            MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build())
                .get()
                .uri("/v1/operations")
                .header("Authorization", TestCredentials.issueOperatorSession(jdbcTemplate))
                .exchange()
                .getResponse()
                .getContentAsByteArray(),
            StandardCharsets.UTF_8);
    Set<String> listedNames =
        Set.copyOf(JsonPath.<List<String>>read(operations, "$.jobs[*].job_name"));

    String alerting = Files.readString(ALERTING);
    Set<String> alertedNames =
        JOB_NAME_MATCHER
            .matcher(alerting)
            .results()
            .flatMap(match -> Arrays.stream(match.group(1).split("\\|")))
            .collect(Collectors.toSet());

    assertThat(registeredNames).isEqualTo(listedNames);
    assertThat(alertedNames).containsAll(registeredNames);
  }
}
