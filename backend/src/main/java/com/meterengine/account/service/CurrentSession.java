package com.meterengine.account.service;

import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.entity.UserSession;
import com.meterengine.organization.entity.Organization;
import java.util.List;

public record CurrentSession(
    UserSession session,
    UserAccount userAccount,
    Organization organization,
    List<Organization> organizations,
    String organizationRole,
    List<String> scopes) {

  public CurrentSession(
      UserSession session,
      UserAccount userAccount,
      Organization organization,
      List<Organization> organizations) {
    this(session, userAccount, organization, organizations, null, List.of());
  }

  public CurrentSession(
      UserSession session,
      UserAccount userAccount,
      Organization organization,
      List<Organization> organizations,
      String organizationRole) {
    this(session, userAccount, organization, organizations, organizationRole, List.of());
  }

  public CurrentSession {
    organizations = List.copyOf(organizations);
    scopes = List.copyOf(scopes);
  }
}
