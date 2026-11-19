package com.meterengine.dashboard.config;

import com.meterengine.dashboard.client.DashboardInsightsStore;
import com.meterengine.dashboard.client.FileDashboardInsightsStore;
import com.meterengine.dashboard.client.S3DashboardInsightsStore;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class DashboardInsightsStoreConfig {

  // 홈 요청이 S3를 오래 기다리지 않게 짧게 끊는다. 실패하면 DB에서 계산해 답한다.
  private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(2);

  // 자격 증명은 SDK 기본 체인이 찾는다. 배포 서버에서는 EC2 인스턴스 역할이다.
  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "meterengine.insights.store", havingValue = "s3")
  S3Client dashboardInsightsS3Client(DashboardInsightsProperties properties) {
    return S3Client.builder()
        .region(Region.of(properties.s3Region()))
        .overrideConfiguration(
            override ->
                override
                    .apiCallTimeout(API_CALL_TIMEOUT)
                    .apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT))
        .build();
  }

  @Bean
  DashboardInsightsStore dashboardInsightsStore(
      DashboardInsightsProperties properties,
      ObjectProvider<S3Client> s3Client,
      JsonMapper jsonMapper) {
    return switch (properties.store()) {
      case FILE -> new FileDashboardInsightsStore(properties.directory(), jsonMapper);
      case S3 ->
          new S3DashboardInsightsStore(s3Client.getObject(), properties.bucket(), jsonMapper);
    };
  }
}
