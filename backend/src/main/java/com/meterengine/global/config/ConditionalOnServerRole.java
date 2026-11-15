package com.meterengine.global.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

// meterengine.role이 적은 역할 가운데 하나일 때만 빈을 만든다.
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(ServerRoleCondition.class)
public @interface ConditionalOnServerRole {

  ServerRole[] value();
}
