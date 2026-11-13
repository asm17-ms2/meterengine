package com.meterengine.mail.config;

import com.meterengine.mail.client.SesEmailSender;
import com.meterengine.mail.service.EmailSender;
import com.meterengine.mail.service.LoggingEmailSender;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration(proxyBeanMethods = false)
public class MailConfig {

  // 발송 스레드가 SES를 오래 기다리면 뒤의 메일이 대기열에 쌓이므로 짧게 끊는다.
  private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(4);

  // 자격 증명은 SDK 기본 체인이 찾는다. 배포 서버에서는 EC2 인스턴스 역할이다.
  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "meterengine.mail.sender", havingValue = "ses")
  SesV2Client sesV2Client(MailProperties properties) {
    return SesV2Client.builder()
        .region(Region.of(properties.sesRegion()))
        .overrideConfiguration(
            override ->
                override
                    .apiCallTimeout(API_CALL_TIMEOUT)
                    .apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT))
        .build();
  }

  @Bean
  EmailSender emailSender(MailProperties properties, ObjectProvider<SesV2Client> ses) {
    return switch (properties.sender()) {
      case LOG -> new LoggingEmailSender();
      case SES -> new SesEmailSender(ses.getObject(), properties.from());
    };
  }
}
