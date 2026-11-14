package com.meterengine.scheduling.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class SchedulingConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class);

  @Test
  void 설정이_없으면_예약_작업을_켠다() {
    contextRunner.run(
        context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
  }

  @Test
  void 설정이_false면_예약_작업을_끈다() {
    contextRunner
        .withPropertyValues("meterengine.scheduling.enabled=false")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
  }

  @Test
  void 역할이_all이면_예약_작업을_켠다() {
    contextRunner
        .withPropertyValues("meterengine.role=all")
        .run(
            context ->
                assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
  }

  @Test
  void 역할이_api면_예약_작업을_켠다() {
    contextRunner
        .withPropertyValues("meterengine.role=api")
        .run(
            context ->
                assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
  }

  // 수집 서버는 요청량에 따라 여러 대가 뜨므로, 여기서 켜지면 같은 예약 작업이 서버마다 겹쳐 돈다.
  @Test
  void 역할이_ingest면_예약_작업을_끈다() {
    contextRunner
        .withPropertyValues("meterengine.role=ingest")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
  }

  @Test
  void 역할이_consumer면_예약_작업을_끈다() {
    contextRunner
        .withPropertyValues("meterengine.role=consumer")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
  }

  @Test
  void 예약_스레드_풀은_예약_작업_수_이상이다() throws IOException {
    int poolSize =
        Integer.parseInt(
            PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))
                .getProperty("spring.task.scheduling.pool.size"));
    int scheduledMethods =
        new ClassPathScanningCandidateComponentProvider(true)
            .findCandidateComponents("com.meterengine").stream()
                .map(AnnotatedBeanDefinition.class::cast)
                .mapToInt(
                    bean ->
                        bean.getMetadata().getAnnotatedMethods(Scheduled.class.getName()).size())
                .sum();

    assertThat(scheduledMethods).isPositive();
    assertThat(poolSize).isGreaterThanOrEqualTo(scheduledMethods);
  }
}
