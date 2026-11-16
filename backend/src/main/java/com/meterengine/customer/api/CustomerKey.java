package com.meterengine.customer.api;

import java.util.UUID;

// 고객은 도입사의 환경 안에 있다. 도입사와 환경을 같이 넘겨야 다른 도입사의 고객을 읽지 않는다.
public record CustomerKey(UUID organizationId, UUID environmentId, UUID customerId) {}
