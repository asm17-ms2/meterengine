package com.meterengine.global.error;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

  @Test
  void 종류_클래스는_같은_상태의_code만_받는다() {
    assertThatNoException().isThrownBy(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));
    assertThatNoException().isThrownBy(() -> new ConflictException(ErrorCode.CUSTOMER_HAS_EVENTS));
    assertThatNoException()
        .isThrownBy(() -> new InvalidRequestException(ErrorCode.UNKNOWN_ORGANIZATION));
  }

  @Test
  void 상태가_다른_code를_넣으면_만들_때_실패한다() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new NotFoundException(ErrorCode.CUSTOMER_HAS_EVENTS));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConflictException(ErrorCode.CUSTOMER_NOT_FOUND));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InvalidRequestException(ErrorCode.CUSTOMER_NOT_FOUND));
  }
}
