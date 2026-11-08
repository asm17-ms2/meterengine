package com.meterengine.event.client;

public interface IngestWarmupClient extends AutoCloseable {

  void sendEvent();

  void sendEventBatch();

  @Override
  void close();
}
