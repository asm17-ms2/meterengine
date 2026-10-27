package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.organization.entity.OrganizationMember;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessScopesTest {
  @Test
  void 관리는_알려진_권한만_포함하고_플랫폼이나_오타_권한을_만들지_않는다() {
    assertThat(AccessScopes.grants(List.of("admin"), "read")).isTrue();
    assertThat(AccessScopes.grants(List.of("admin"), "ingest")).isTrue();
    for (String scope : List.of("operator", "owner", "write", ""))
      assertThat(AccessScopes.grants(List.of("admin"), scope)).isFalse();
    assertThat(AccessScopes.grants(List.of("admin"), null)).isFalse();
    assertThat(AccessScopes.grants(List.of("read"), "admin")).isFalse();
  }

  @Test
  void 권한의_순서는_보존하고_중복과_잘못된_입력을_구분한다() {
    assertThat(AccessScopes.normalize(List.of("read", "ingest", "read")))
        .containsExactly("read", "ingest");
    for (List<String> invalid :
        List.of(List.<String>of(), List.of("operator"), Arrays.asList("read", null)))
      assertThatThrownBy(() -> AccessScopes.normalize(invalid))
          .isInstanceOfSatisfying(
              InvalidRequestException.class,
              error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    assertThatThrownBy(() -> AccessScopes.normalize(null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void 조회_소속의_보관과_복원은_관리권한을_추가하지_않는다() {
    OrganizationMember member =
        new OrganizationMember(
            UUID.randomUUID(), UUID.randomUUID(), OffsetDateTime.now(), List.of("read"));
    member.revoke(OffsetDateTime.now());
    member.restore();
    assertThat(member.getScopes()).containsExactly("read");
    assertThat(member.getRole()).isEqualTo("member");
  }
}
