package com.meterengine.event.service;

import com.meterengine.event.client.IngestWarmupClient;
import com.meterengine.event.client.IngestWarmupClients;
import com.meterengine.event.config.IngestWarmupProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IngestWarmupService implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(IngestWarmupService.class);

  private final IngestWarmupProperties ingestWarmupProperties;
  private final IngestWarmupClients ingestWarmupClients;
  private final Clock clock;

  IngestWarmupService(
      IngestWarmupProperties ingestWarmupProperties,
      IngestWarmupClients ingestWarmupClients,
      Clock clock) {
    this.ingestWarmupProperties = ingestWarmupProperties;
    this.ingestWarmupClients = ingestWarmupClients;
    this.clock = clock;
  }

  // 준비 신호가 열리기 전에 돌아 ALB가 데워진 서버에만 보낸다. 실패해도 기동은 이어간다.
  @Override
  public void run(ApplicationArguments args) {
    if (!StringUtils.hasText(ingestWarmupProperties.apiKey())) {
      logger.info("Ingest warm-up skipped: no api key");
      return;
    }
    Instant startedAt = clock.instant();
    Instant deadline = startedAt.plus(ingestWarmupProperties.timeLimit());
    int sent = 0;
    try (IngestWarmupClient client = open()) {
      while (sent < ingestWarmupProperties.requests() && clock.instant().isBefore(deadline)) {
        send(client, sent);
        sent++;
      }
      logger.info(
          "Ingest warm-up finished: requests={}, elapsed={}ms",
          sent,
          Duration.between(startedAt, clock.instant()).toMillis());
    } catch (RuntimeException exception) {
      logger.warn("Ingest warm-up stopped after {} requests", sent, exception);
    }
  }

  private IngestWarmupClient open() {
    return ingestWarmupClients.open(
        ingestWarmupProperties.apiKey(),
        UUID.fromString(ingestWarmupProperties.organizationId()),
        UUID.fromString(ingestWarmupProperties.customerId()));
  }

  private static void send(IngestWarmupClient client, int sequence) {
    if (sequence % 2 == 0) {
      client.sendEvent();
    } else {
      client.sendEventBatch();
    }
  }
}
