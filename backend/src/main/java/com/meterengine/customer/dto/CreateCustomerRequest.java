package com.meterengine.customer.dto;

import com.meterengine.customer.entity.Customer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 고객 등록의 요청 본문 (MS2-155).
 *
 * <p>id는 받지 않는다. 서버가 UUID를 발급하고, 수정과 삭제는 경로에서 대상을 지목한다. 도입사가 id를 정하게 하면 남의 도입사 고객의 id를 넘겨 무슨 일이
 * 벌어지는지 시험할 수 있다.
 *
 * @param name 고객 이름. 화면과 청구서에 그대로 나가는 값이다
 */
public record CreateCustomerRequest(@NotBlank @Size(max = Customer.NAME_MAX_LENGTH) String name) {}
