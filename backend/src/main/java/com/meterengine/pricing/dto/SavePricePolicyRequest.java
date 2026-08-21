package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 가격 정책 등록의 요청 본문 (MS2-157).
 *
 * <p>정책(축 선언)만 받는다. 단가는 유효 기간 같은 자체 수명을 가질 예정이라 등록 시점이 정책과 다르고, MS2-177의 단가 API가 따로 등록한다 (PR 43 리뷰
 * 결정). 단가가 아직 없는 미터는 청구 예정액 계산이 라인에서 제외한다 ({@code DraftInvoiceService}).
 *
 * <p>여기 애노테이션은 필드 형식만 본다(400 validation_error). 선언 안의 중복 키와 빈 키는 서비스가 검증한다(400
 * invalid_price_policy).
 *
 * @param dimensionProperties 이 미터의 가격을 가르는 이벤트 속성 키의 집합. 무차원이면 빈 배열이다
 */
public record SavePricePolicyRequest(
    @NotNull @JsonProperty("dimension_properties") List<String> dimensionProperties) {}
