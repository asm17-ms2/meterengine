package com.meterengine.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AcceptSessionConsentsRequest(
    @NotEmpty @Valid List<@NotNull CreateSignupConsentRequest> consents) {}
