package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestClock;
import com.meterengine.event.client.IngestWarmupClient;
import com.meterengine.event.client.IngestWarmupClients;
import com.meterengine.event.config.IngestWarmupProperties;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class IngestWarmupServiceTest {

  private static final String API_KEY = "me_live_warmupwarmupwarmupwarmupwarmup";
  private static final String ORGANIZATION_ID = UUID.randomUUID().toString();
  private static final String CUSTOMER_ID = UUID.randomUUID().toString();
  private static final Duration TIME_LIMIT = Duration.ofSeconds(30);
  private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-11-07T00:00:00Z");

  private final TestClock clock = new TestClock();
  private final RecordingClients clients = new RecordingClients();

  @BeforeEach
  void setUp() {
    clock.fix(STARTED_AT);
  }

  @Test
  void 키가_비었으면_클라이언트를_열지_않는다(CapturedOutput output) {
    run(new IngestWarmupProperties("", ORGANIZATION_ID, CUSTOMER_ID, 5, TIME_LIMIT));

    assertThat(clients.opened).isZero();
    assertThat(output).contains("Ingest warm-up skipped: no api key");
  }

  @Test
  void 정한_요청_수만큼_보낸다() {
    run(properties(5));

    assertThat(clients.calls).hasSize(5);
  }

  @Test
  void 끝나면_보낸_요청_수를_INFO로_남긴다(CapturedOutput output) {
    run(properties(4));

    assertThat(output).contains("INFO").contains("Ingest warm-up finished: requests=4, elapsed=");
  }

  @Test
  void 단건과_묶음을_번갈아_보낸다() {
    run(properties(4));

    assertThat(clients.calls).containsExactly("event", "batch", "event", "batch");
  }

  @Test
  void 제한_시간이_지나면_남은_요청을_보내지_않는다() {
    clients.onSend =
        () -> clock.fix(clock.instant().plusSeconds(1).atOffset(STARTED_AT.getOffset()));

    run(
        new IngestWarmupProperties(
            API_KEY, ORGANIZATION_ID, CUSTOMER_ID, 10, Duration.ofSeconds(2)));

    assertThat(clients.calls).hasSize(2);
  }

  @Test
  void 첫_실패에서_멈추고_WARN을_남긴다(CapturedOutput output) {
    clients.failAt(
        3,
        () -> {
          throw new IllegalStateException("POST /v1/events returned 503");
        });

    run(properties(10));

    assertThat(clients.calls).hasSize(3);
    assertThat(output).contains("WARN").contains("Ingest warm-up stopped after 2 requests");
  }

  @Test
  void id가_UUID가_아니면_열지_않고_WARN을_남긴다(CapturedOutput output) {
    run(new IngestWarmupProperties(API_KEY, "not-a-uuid", CUSTOMER_ID, 5, TIME_LIMIT));

    assertThat(clients.opened).isZero();
    assertThat(output).contains("WARN").contains("Ingest warm-up stopped after 0 requests");
  }

  @Test
  void 끝나면_클라이언트를_닫는다() {
    run(properties(2));

    assertThat(clients.closed).isEqualTo(1);
  }

  @Test
  void 실패해도_클라이언트를_닫는다() {
    clients.failAt(
        1,
        () -> {
          throw new IllegalStateException("POST /v1/events returned 403");
        });

    run(properties(2));

    assertThat(clients.closed).isEqualTo(1);
  }

  @Test
  void Error는_잡지_않는다() {
    clients.failAt(
        1,
        () -> {
          throw new Error("시험용 오류");
        });
    IngestWarmupService service = service(properties(2));

    assertThatThrownBy(() -> service.run(new DefaultApplicationArguments()))
        .isInstanceOf(Error.class)
        .hasMessage("시험용 오류");
  }

  private static IngestWarmupProperties properties(int requests) {
    return new IngestWarmupProperties(API_KEY, ORGANIZATION_ID, CUSTOMER_ID, requests, TIME_LIMIT);
  }

  private IngestWarmupService service(IngestWarmupProperties properties) {
    return new IngestWarmupService(properties, clients, clock);
  }

  private void run(IngestWarmupProperties properties) {
    service(properties).run(new DefaultApplicationArguments());
  }

  private static final class RecordingClients implements IngestWarmupClients {
    private final List<String> calls = new ArrayList<>();
    private int opened;
    private int closed;
    private int failingCall;
    private Runnable failure = () -> {};
    private Runnable onSend = () -> {};

    void failAt(int call, Runnable failure) {
      this.failingCall = call;
      this.failure = failure;
    }

    @Override
    public IngestWarmupClient open(String apiKey, UUID organizationId, UUID customerId) {
      opened++;
      return new IngestWarmupClient() {
        @Override
        public void sendEvent() {
          record("event");
        }

        @Override
        public void sendEventBatch() {
          record("batch");
        }

        @Override
        public void close() {
          closed++;
        }
      };
    }

    private void record(String call) {
      calls.add(call);
      onSend.run();
      if (calls.size() == failingCall) {
        failure.run();
      }
    }
  }
}
