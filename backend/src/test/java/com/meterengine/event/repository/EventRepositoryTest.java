package com.meterengine.event.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EventRepositoryTest {

  @Autowired private EventRepository eventRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 같은_type의_숫자_속성을_가진_이벤트가_있으면_true다() {
    UUID organizationId = insertOrganization();
    insertEvent(organizationId, "chat_completion", "{\"token\": 10}");

    assertThat(
            eventRepository.existsWithNumericProperty(organizationId, "chat_completion", "token"))
        .isTrue();
  }

  @Test
  void 속성_값이_숫자가_아니면_집계되지_않으므로_false다() {
    UUID organizationId = insertOrganization();
    insertEvent(organizationId, "chat_completion", "{\"token\": \"10\"}");

    assertThat(
            eventRepository.existsWithNumericProperty(organizationId, "chat_completion", "token"))
        .isFalse();
  }

  @Test
  void 다른_도입사의_이벤트는_세지_않는다() {
    UUID organizationId = insertOrganization();
    insertEvent(insertOrganization(), "chat_completion", "{\"token\": 10}");

    assertThat(
            eventRepository.existsWithNumericProperty(organizationId, "chat_completion", "token"))
        .isFalse();
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }

  private void insertEvent(UUID organizationId, String type, String propertiesJson) {
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '고객') RETURNING id",
            UUID.class,
            organizationId);
    jdbcTemplate.update(
        """
        INSERT INTO event (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, ?, ?, ?, ?::jsonb, now())
        """,
        organizationId,
        UUID.randomUUID().toString(),
        customerId,
        type,
        propertiesJson);
  }
}
