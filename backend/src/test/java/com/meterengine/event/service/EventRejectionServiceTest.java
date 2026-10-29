package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.event.controller.EventRejectionController;
import com.meterengine.global.error.ErrorCode;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestParam;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EventRejectionServiceTest {
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 기록하는_거절_사유와_조회_조건과_DB_제약의_사유가_같다() {
    Set<String> recorded =
        EventRejectionService.REASONS.stream().map(ErrorCode::getCode).collect(Collectors.toSet());
    String constraint =
        jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid"
                + " WHERE t.relname = 'event_rejection' AND c.contype = 'c' AND pg_get_constraintdef(c.oid) LIKE '%error_code%'",
            String.class);
    Set<String> stored =
        java.util.regex.Pattern.compile("'([a-z_]+)'")
            .matcher(constraint)
            .results()
            .map(match -> match.group(1))
            .collect(Collectors.toSet());
    String regexp =
        Arrays.stream(EventRejectionController.class.getDeclaredMethods())
            .flatMap(method -> Arrays.stream(method.getParameters()))
            .filter(
                parameter ->
                    parameter.isAnnotationPresent(RequestParam.class)
                        && "error_code".equals(parameter.getAnnotation(RequestParam.class).name()))
            .map(parameter -> parameter.getAnnotation(Pattern.class).regexp())
            .findFirst()
            .orElseThrow();
    Set<String> searchable = Set.of(regexp.split("\\|"));
    assertThat(recorded).isEqualTo(stored).isEqualTo(searchable);
  }
}
