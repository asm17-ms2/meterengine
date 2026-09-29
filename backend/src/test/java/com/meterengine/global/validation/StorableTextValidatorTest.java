package com.meterengine.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StorableTextValidatorTest {

  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  record Probe(@StorableText String text, List<@StorableText String> texts) {}

  @Test
  void NUL_문자가_있으면_거절한다() {
    assertThat(paths(new Probe("a\u0000b", List.of()))).containsExactly("text");
  }

  @Test
  void 짝이_없는_서로게이트가_있으면_거절한다() {
    assertThat(paths(new Probe("a\ud800b", List.of()))).containsExactly("text");
    assertThat(paths(new Probe("a\udc00", List.of()))).containsExactly("text");
    assertThat(paths(new Probe("\ude00\ud83d", List.of()))).containsExactly("text");
  }

  @Test
  void 짝이_맞는_서로게이트와_한국어와_null은_받는다() {
    assertThat(paths(new Probe("아크메 😀", List.of()))).isEmpty();
    assertThat(paths(new Probe(null, null))).isEmpty();
    assertThat(paths(new Probe("", List.of("")))).isEmpty();
  }

  @Test
  void 목록은_원소마다_검사하고_몇_번째인지_알려준다() {
    assertThat(paths(new Probe("ok", List.of("model", "a\u0000b"))))
        .containsExactly("texts[1].<list element>");
  }

  @Test
  void 문구는_받지_않는_문자를_알려준다() {
    assertThat(validator.validate(new Probe("a\u0000b", List.of())))
        .extracting(ConstraintViolation::getMessage)
        .containsExactly("NUL 문자나 짝이 없는 UTF-16 서로게이트는 받지 않습니다");
  }

  private static List<String> paths(Probe probe) {
    Set<ConstraintViolation<Probe>> violations = validator.validate(probe);
    return violations.stream().map(violation -> violation.getPropertyPath().toString()).toList();
  }
}
