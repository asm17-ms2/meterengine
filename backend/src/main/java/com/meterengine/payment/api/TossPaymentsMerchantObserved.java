package com.meterengine.payment.api;

import java.util.UUID;

// 실제 결제가 끝난 토스페이먼츠 응답에서 본 상점아이디다. 결제 쪽은 알리기만 하고 견주지 않는다.
public record TossPaymentsMerchantObserved(UUID organizationId, String merchantId) {}
