package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RequestAuditTargetCoverageTest {
  private static final Set<String> NOT_AUDIT_TARGETS = Set.of("code", "token", "mode");
  private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}:]+)");

  @Autowired
  @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;

  @Test
  void v1_경로_변수는_요청_감사_대상이거나_대상에서_뺀다고_정한_이름이다() {
    Set<String> variables =
        handlerMapping.getHandlerMethods().keySet().stream()
            .flatMap(mapping -> mapping.getPatternValues().stream())
            .filter(pattern -> pattern.startsWith("/v1/"))
            .flatMap(
                pattern -> PATH_VARIABLE.matcher(pattern).results().map(match -> match.group(1)))
            .collect(Collectors.toSet());

    assertThat(variables).isNotEmpty();
    assertThat(variables)
        .filteredOn(
            variable ->
                !RequestAuditFilter.UUID_VARIABLES.contains(variable)
                    && !NOT_AUDIT_TARGETS.contains(variable))
        .isEmpty();
  }
}
