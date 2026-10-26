package com.meterengine.pricing.dto;

import java.util.List;
import java.util.UUID;

public record CustomerCharge(
    UUID customerId,
    long amount,
    List<DimensionCharge> dimensionCharges,
    List<UnpricedUsage> unpricedUsages,
    List<NegativeUsage> negativeUsages) {}
