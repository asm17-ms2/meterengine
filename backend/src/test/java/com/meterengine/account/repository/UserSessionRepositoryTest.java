package com.meterengine.account.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class UserSessionRepositoryTest {

  @Test
  void 도입사_전환과_로그아웃이_잡는_세션_조회는_쓰기_잠금을_건다() throws NoSuchMethodException {
    Lock lock =
        UserSessionRepository.class
            .getMethod("findForUpdateById", UUID.class)
            .getAnnotation(Lock.class);

    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }
}
