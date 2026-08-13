package com.meterengine.usage;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 한 미터에 대한 고객 한 명의 기간 사용량 (MS2-129).
 *
 * <p>quantity가 BigDecimal인 이유: properties의 숫자는 jsonb에 numeric으로 담겨 자릿수가 그대로 남는다(MS2-130). 토큰처럼 정수인
 * 값만 오리라 가정하고 long으로 좁히면 소수를 실어 보내는 미터가 생겼을 때 청구 근거가 조용히 잘린다.
 *
 * @param customerName 집계 시점의 고객 이름. 화면이 UUID 대신 보여줄 것이 필요해서 함께 싣는다
 */
public record CustomerUsage(UUID customerId, String customerName, BigDecimal quantity) {}
