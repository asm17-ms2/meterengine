package com.meterengine.account.service;

import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.entity.UserSession;
import com.meterengine.organization.entity.Organization;
import java.util.List;

public record CurrentSession(
    UserSession session,
    UserAccount userAccount,
    Organization organization,
    List<Organization> organizations) {

  public CurrentSession {
    organizations = List.copyOf(organizations);
  }
}
