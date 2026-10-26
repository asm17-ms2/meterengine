package com.meterengine.account.service;

import com.meterengine.account.entity.UserAccount;
import com.meterengine.organization.entity.Organization;

public record Signup(Organization organization, UserAccount userAccount) {}
