package com.meterengine.scheduling.config;

import com.meterengine.global.config.ConditionalOnServerRole;
import com.meterengine.global.config.ServerRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 예약 작업은 한 역할에서만 돈다. 수집 서버와 컨슈머 서버가 여러 대여도 같은 작업이 겹쳐 돌지 않는다.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "meterengine.scheduling.enabled", matchIfMissing = true)
@ConditionalOnServerRole({ServerRole.ALL, ServerRole.API})
public class SchedulingConfig {}
