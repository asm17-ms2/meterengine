package com.meterengine.account.service;

import com.meterengine.account.entity.UserAccount;
import com.meterengine.organization.entity.Organization;
import java.util.List;

public record UserAccountMemberships(UserAccount userAccount, List<Organization> organizations) {

  public UserAccountMemberships {
    organizations = List.copyOf(organizations);
  }
}
