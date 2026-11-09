package com.meterengine.event.client;

import java.util.UUID;

public interface IngestWarmupClients {

  IngestWarmupClient open(String apiKey, UUID organizationId, UUID customerId);
}
