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

// NUL 문자와 짝이 없는 UTF-16 서로게이트가 없는 문자열만 통과시킨다.
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = StorableTextValidator.class)
public @interface StorableText {

  String message() default "NUL 문자나 짝이 없는 UTF-16 서로게이트는 받지 않습니다";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
