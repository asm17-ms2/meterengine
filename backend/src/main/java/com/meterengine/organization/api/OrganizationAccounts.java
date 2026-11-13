package com.meterengine.organization.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationAccounts {
  OrganizationPerson get(UUID userAccountId);

  Optional<OrganizationPerson> find(String email);

  // 계정 행을 잠가 역할 변경과 겹치지 않게 읽는다.
  Optional<OrganizationPerson> lock(String email);

  boolean exists(UUID userAccountId);

  OrganizationPerson create(String email, String name, String password);

  OrganizationPerson register(
      String email, String name, String password, List<MembershipConsent> consents);

  void revoke(UUID organizationId, UUID userAccountId, OffsetDateTime revokedAt);
}
