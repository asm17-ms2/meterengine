package com.meterengine.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 고객 등록과 수정의 요청 본문 (MS2-155).
 *
 * <p>등록과 수정이 같은 본문을 쓴다. 고칠 수 있는 것이 이름뿐이라 두 요청의 모양이 완전히 같고, 따로 두면 한쪽 검증만 고치는 일이 생긴다. 필드가 늘어나면서 "등록
 * 때만 정하고 이후 못 고치는 값"이 생기면 그때 나눈다.
 *
 * <p>id는 받지 않는다. 서버가 UUID를 발급하고, 수정과 삭제는 경로에서 대상을 지목한다. 도입사가 id를 정하게 하면 남의 도입사 고객의 id를 넘겨 무슨 일이
 * 벌어지는지 시험할 수 있다.
 *
 * @param name 고객 이름. 화면과 청구서에 그대로 나가는 값이다
 */
public record SaveCustomerRequest(
    /**
     * 상한 255는 컬럼이 아니라 앱이 정한다. customer.name은 길이 제한 없는 VARCHAR라 DB는 무엇이든 받는다. 상한이 없으면 거절 기준도 없어서, 화면
     * 레이아웃과 로그를 무너뜨리는 값이 그대로 저장된다. 이 값은 수집 API의 transaction_id 상한과 같은 관례값이며, 그쪽과 달리 DB가 강제하지는 않는다.
     */
    @NotBlank @Size(max = 255) String name) {}
