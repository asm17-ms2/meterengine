package com.meterengine;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 형식 검증에 걸린 필드 하나. {@link ProblemResponse#errors()}의 원소이며 문서 전용이다 (MS2-140).
 *
 * <p><b>{@code field}는 도입사가 보낸 이름이다</b> (MS2-150 5단계, 팀 결정 A-2). 본문이면 JSON 키({@code event_type}),
 * 쿼리 파라미터와 헤더면 요청에 실린 이름이다. 검증은 자바 이름 위에서 돌지만 그 이름은 여기까지 오지 않는다. 되찾는 방법은 {@code
 * FrameworkExceptionHandler}의 {@code wireName}과 {@code parameterWireName}에 있다.
 *
 * <p>그래서 example을 {@code event_type}으로 둔다. 자바 이름과 와이어 이름이 갈리는 필드라, 변환이 깨지면 이 example이 가리키는 것과 실제
 * 응답이 어긋난다. 두 표기가 같은 이름({@code timestamp})을 example로 쓰면 그 회귀를 문서만 보고는 알 수 없다.
 *
 * <p><b>{@link ProblemResponse}의 중첩 레코드로 두지 않았다. 다만 이 배치는 계약에 아무 차이도 만들지 않는다.</b> springdoc은 중첩
 * 레코드도 최상위 컴포넌트로 끌어올리고 {@code errors}를 {@code $ref}로 가리키게 한다. 그래서 중첩으로 옮겨 재생성해도 {@code
 * openapi.yaml}이 바이트 단위로 같다 (MS2-150 [0-B] 17에서 레포 복사본으로 확인). 자바 쪽 파일 배치일 뿐이고, 바꿀 이유가 없어 그대로 둔다.
 *
 * <p><b>[정정 2026-08-17, MS2-150 7단계]</b> 예전 이 자리에는 "중첩으로 두면 springdoc이 {@code type: object}를 안
 * 붙인다(<b>실측</b>)"고 적혀 있었다. <b>틀렸다.</b> 확인하지 않은 도구 동작을 "실측"이라 적어 팀의 공유 지식으로 굳힐 뻔한 자리다(유형 K).
 *
 * <p>고치면서 한 번 더 틀렸다. 처음 정정문은 근거를 "문서에서 평평하게 보인다"로 갈아 끼웠는데, <b>생성물이 바이트 단위로 같다는 바로 아래 문장이 그것을
 * 반박한다.</b> 문서상 이득이 없으니 문서를 근거로 들 수 없다. 틀린 근거를 다른 틀린 근거로 바꾸면 고치기 전보다 나빠진다(P-9).
 *
 * @param field 걸린 자리의 이름. 도입사가 보낸 표기 그대로다 (JSON 키, 쿼리 파라미터명, 헤더명)
 * @param message 걸린 이유. 한국어로 고정이며(MS2-150 6단계) 사람이 읽는 문구라 문면이 바뀔 수 있다. FE가 이 문자열을 키로 분기하면 안 된다
 */
@Schema(description = "형식 검증에 걸린 필드 하나")
public record ProblemFieldError(
    @Schema(example = "event_type") String field, @Schema(example = "공백일 수 없습니다") String message) {}
