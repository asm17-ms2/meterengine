package com.meterengine.apikey.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class ApiKeyRepositoryTest {

  @Test
  void 교체와_폐기가_잡는_키_조회는_쓰기_잠금을_건다() throws NoSuchMethodException {
    Lock lock =
        ApiKeyRepository.class
            .getMethod("findForUpdateByIdAndOrganizationId", UUID.class, UUID.class)
            .getAnnotation(Lock.class);

    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }
}
