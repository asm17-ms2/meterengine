package com.meterengine;

/**
 * 오류 응답 본문의 멤버 이름 (MS2-150).
 *
 * <p><b>{@link ErrorCodes}와 나눈 이유.</b> 저쪽은 {@code code}에 담기는 <b>값</b>의 어휘이고, 이쪽은 그 값이 실리는 <b>자리의
 * 이름</b>이다. 둘을 한 클래스에 담으면 {@code ErrorCodes.FIELD}처럼 이름이 뜻과 어긋난다. 바뀌는 이유와 시점도 다르다. 어휘는 오류 종류가 늘 때
 * 늘어나고, 멤버 이름은 계약을 갈아엎을 때만 바뀐다.
 *
 * <p><b>왜 리터럴로 두지 않나.</b> 이 이름들은 두 곳에서 따로 적힌다. 응답을 만드는 advice의 {@code setProperty} / {@code
 * Map.of}와, 문서 스키마를 선언하는 {@link ProblemResponse}와 {@link ProblemFieldError}의 레코드 컴포넌트다. 한쪽만 고치면 응답과
 * 문서가 갈리는데, <b>지금 테스트는 문서만 보므로 그 갈림을 잡지 못한다</b> (MS2-150 [0-B] 19). 상수로 묶으면 최소한 응답 쪽과 검사 쪽이 함께
 * 움직인다.
 *
 * <p>레코드 컴포넌트 이름 자체는 상수로 못 만든다(자바 문법). 그래서 이 상수와 레코드 필드명이 같은지는 사람이 지켜야 하고, 그 짝을 검사하는 것이 MS2-150
 * 인수기준 6의 키 집합 대조다.
 *
 * <p>{@code type}, {@code title}, {@code status}, {@code detail}, {@code instance}는 여기 없다. 프레임워크가
 * 직렬화하는 {@code ProblemDetail}의 고정 필드라 우리가 이름을 쓰는 자리가 없다.
 *
 * <p>배치는 {@link ErrorCodes}와 같은 이유로 루트다. MS2-149가 정한 도메인 패키지 넷(customer, event, invoice, metric) 중
 * 어디에도 속하지 않아 루트에 남긴다 (MS2-141은 취소됨).
 */
public final class ProblemMembers {

  /** 기계 판독용 오류 종류. 값의 어휘는 {@link ErrorCodes}에 있다. */
  public static final String CODE = "code";

  /** 형식 검증에 걸린 필드 목록. {@code code=validation_error}일 때만 실린다. */
  public static final String ERRORS = "errors";

  /** {@link #ERRORS} 원소의 필드 이름. */
  public static final String FIELD = "field";

  /** {@link #ERRORS} 원소의 사유 문구. */
  public static final String MESSAGE = "message";

  private ProblemMembers() {}
}
