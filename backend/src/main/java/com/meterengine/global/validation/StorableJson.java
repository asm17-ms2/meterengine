package com.meterengine.global.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

// 키와 문자열 값에 NUL 문자도 짝이 없는 UTF-16 서로게이트도 없고, 숫자가 소수점 앞 131072자리와 소수점 뒤 16383자리 안인 JSON 객체만 통과시킨다.
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = StorableJsonValidator.class)
public @interface StorableJson {

  String message() default
      "키와 문자열 값의 NUL 문자와 짝이 없는 UTF-16 서로게이트, 소수점 앞 131072자리나 소수점 뒤 16383자리를 넘는 숫자는 받지 않습니다";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
