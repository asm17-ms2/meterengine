package com.meterengine.event.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.MeteringEnvironmentTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EventRepositoryTest extends MeteringEnvironmentTestSupport {
  @Autowired private EventRepository eventRepository;

  @Test
  void 같은_type의_숫자_속성을_가진_이벤트가_있으면_true다() {
    transactions.write(
        scope("test"),
        () -> {
          insertEvent(organizationId, "chat_completion", "{\"token\":10}");
          assertThat(
                  eventRepository.existsWithNumericProperty(
                      organizationId, "chat_completion", "token"))
              .isTrue();
          return null;
        });
  }

  @Test
  void 속성_값이_숫자가_아니면_집계되지_않으므로_false다() {
    transactions.write(
        scope("test"),
        () -> {
          insertEvent(organizationId, "chat_completion", "{\"token\":\"10\"}");
          assertThat(
                  eventRepository.existsWithNumericProperty(
                      organizationId, "chat_completion", "token"))
              .isFalse();
          return null;
        });
  }

  @Test
  void 다른_도입사의_이벤트는_세지_않는다() {
    UUID other = UUID.randomUUID();
    jdbc.update("INSERT INTO shared.organization(id,name) VALUES (?,'다른 도입사')", other);
    transactions.write(
        scope("test"),
        () -> {
          insertEvent(other, "chat_completion", "{\"token\":10}");
          assertThat(
                  eventRepository.existsWithNumericProperty(
                      organizationId, "chat_completion", "token"))
              .isFalse();
          return null;
        });
  }

  @Test
  void 마이그레이션이_끝나면_event의_FK와_검사_제약은_검증된_상태다() {
    transactions.read(
        scope("test"),
        () -> {
          assertThat(
                  jdbc.queryForList(
                      "SELECT conname FROM pg_constraint WHERE conrelid='event'::regclass AND convalidated",
                      String.class))
              .contains(
                  "event_organization_fk",
                  "event_customer_same_environment_fk",
                  "event_actor_type_check",
                  "event_actor_identity_check");
          return null;
        });
  }

  private void insertEvent(UUID organization, String type, String properties) {
    UUID customer = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO customer(id,organization_id,name) VALUES (?,?,'고객')", customer, organization);
    jdbc.update(
        "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at) VALUES (?,?,?,?,?::jsonb,now())",
        organization,
        UUID.randomUUID().toString(),
        customer,
        type,
        properties);
  }
}
