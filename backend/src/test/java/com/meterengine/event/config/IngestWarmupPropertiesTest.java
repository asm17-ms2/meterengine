package com.meterengine.event.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

class IngestWarmupPropertiesTest {

  private static final String API_KEY = "me_live_warmupwarmupwarmupwarmupwarmup";

  @Test
  void 문자열로_바꿔도_키_원문이_나오지_않는다() {
    var properties =
        new IngestWarmupProperties(API_KEY, "organization", "customer", 1, Duration.ofSeconds(30));

    assertThat(properties.toString()).doesNotContain(API_KEY);
  }

  @Test
  void 단위_없는_시간_상한은_초로_읽는다() {
    var source =
        new MapConfigurationPropertySource(Map.of("meterengine.ingest-warmup.time-limit", "30"));

    var properties =
        new Binder(source).bind("meterengine.ingest-warmup", IngestWarmupProperties.class).get();

    assertThat(properties.timeLimit()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void 설정_파일의_기본_요청_수는_1000이다() throws IOException {
    assertThat(bindApplicationProperties().requests()).isEqualTo(1000);
  }

  @Test
  void 설정_파일의_기본_시간_상한은_20초다() throws IOException {
    assertThat(bindApplicationProperties().timeLimit()).isEqualTo(Duration.ofSeconds(20));
  }

  // 환경변수를 빼고 application.properties만 읽어 기본값을 본다.
  private static IngestWarmupProperties bindApplicationProperties() throws IOException {
    var sources = new MutablePropertySources();
    new PropertiesPropertySourceLoader()
        .load("application", new ClassPathResource("application.properties"))
        .forEach(sources::addLast);
    var binder =
        new Binder(
            ConfigurationPropertySources.from(sources),
            new PropertySourcesPlaceholdersResolver(sources));
    return binder.bind("meterengine.ingest-warmup", IngestWarmupProperties.class).get();
  }
}
