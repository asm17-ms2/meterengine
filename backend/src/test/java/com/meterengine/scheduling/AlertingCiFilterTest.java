package com.meterengine.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AlertingCiFilterTest {
  private static final Path CI_WORKFLOW = Path.of("..", ".github", "workflows", "ci.yml");

  @Test
  void 경보_규칙만_바뀐_PR에서도_CI가_작업_이름_시험을_돌린다() throws Exception {
    String workflow = Files.readString(CI_WORKFLOW);
    String filters = workflow.substring(workflow.indexOf("filters: |"));
    String backendFilter =
        filters.substring(filters.indexOf("backend:"), filters.indexOf("frontend:"));

    assertThat(backendFilter).contains("'deploy/grafana/provisioning/alerting/alerting.yml'");
  }
}
