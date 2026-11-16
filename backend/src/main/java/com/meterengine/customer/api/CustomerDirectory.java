package com.meterengine.customer.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface CustomerDirectory {
  boolean exists(UUID organizationId, UUID customerId);

  // 고객 행에 FOR SHARE 잠금을 걸어 보관과 엇갈리지 않게 한 뒤 쓸 수 있는지 확인한다.
  void check(UUID organizationId, UUID customerId);

  // 고객 행에 FOR SHARE 잠금을 걸고 고객마다 보관하지 않았는지를 돌려준다.
  Map<UUID, Boolean> active(UUID organizationId, Collection<UUID> customerIds);

  // 여러 도입사의 고객을 한 번에 본다. 고객 행에 FOR SHARE 잠금을 걸고, 그 도입사의 그 환경에 있는 고객만 돌려준다.
  Map<CustomerKey, Boolean> active(Collection<CustomerKey> customerKeys);
}
