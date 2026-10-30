package com.meterengine.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meterengine.account.config.AuthRateLimitProperties;
import com.meterengine.account.repository.AuthRequestRateLimitRepository;
import com.meterengine.account.repository.AuthRequestRateLimitRepository.Bucket;
import com.meterengine.account.repository.AuthRequestRateLimitRepository.Rejection;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.TooManyRequestsException;
import com.meterengine.global.security.SecureTokens;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
class AuthRequestRateLimitServiceTest {
  private final AuthRequestRateLimitRepository repository =
      mock(AuthRequestRateLimitRepository.class);
  private final ArgumentCaptor<Bucket> address = ArgumentCaptor.forClass(Bucket.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<List<Bucket>> accounts = ArgumentCaptor.forClass(List.class);

  @Test
  void 기본값은_직접_peer를_쓰고_외부가_보낸_IP_헤더는_신뢰하지_않는다() {
    var service = service(List.of());
    var request = request("203.0.113.10");
    request.addHeader(AuthRequestRateLimitService.CLIENT_IP_HEADER, "198.51.100.11");
    request.addHeader("X-Forwarded-For", "198.51.100.12");
    assertThat(service.clientAddress(request)).isEqualTo("203.0.113.10");
  }

  @Test
  void 명시한_CIDR의_peer만_단일_숫자_클라이언트_IP를_전달할_수_있다() {
    var service = service(List.of("10.20.0.0/16"));
    var trusted = request("10.20.10.2");
    trusted.addHeader(AuthRequestRateLimitService.CLIENT_IP_HEADER, "198.51.100.11");
    assertThat(service.clientAddress(trusted)).isEqualTo("198.51.100.11");
    var untrusted = request("10.21.10.2");
    untrusted.addHeader(AuthRequestRateLimitService.CLIENT_IP_HEADER, "198.51.100.11");
    assertThat(service.clientAddress(untrusted)).isEqualTo("10.21.10.2");
  }

  @Test
  void 여러_헤더와_쉼표_호스트명_URL_포트_축약_IP는_peer로_돌린다() {
    var service = service(List.of("127.0.0.1/32"));
    for (String value :
        List.of(
            "203.0.113.1, 198.51.100.1",
            "localhost",
            "https://203.0.113.1",
            "127.1",
            "203.0.113.1:80",
            "203.00.113.1",
            "999.0.0.1")) {
      var request = request("127.0.0.1");
      request.addHeader(AuthRequestRateLimitService.CLIENT_IP_HEADER, value);
      assertThat(service.clientAddress(request)).isEqualTo("127.0.0.1");
    }
    var duplicate = request("127.0.0.1");
    duplicate.addHeader(AuthRequestRateLimitService.CLIENT_IP_HEADER, "203.0.113.1");
    duplicate.addHeader(AuthRequestRateLimitService.CLIENT_IP_HEADER, "203.0.113.2");
    assertThat(service.clientAddress(duplicate)).isEqualTo("127.0.0.1");
  }

  @Test
  void IPv6_동일_주소의_표기를_정규화한다() {
    var service = service(List.of("2001:db8:1::/48"));
    var first = request("2001:db8:1::1");
    first.addHeader(AuthRequestRateLimitService.CLIENT_IP_HEADER, "2001:db8::2");
    var second = request("2001:db8:1:0:0:0:0:1");
    second.addHeader(AuthRequestRateLimitService.CLIENT_IP_HEADER, "2001:db8:0:0:0:0:0:2");
    assertThat(service.clientAddress(first)).isEqualTo("2001:db8:0:0:0:0:0:2");
    assertThat(service.clientAddress(second)).isEqualTo("2001:db8:0:0:0:0:0:2");
    var untrusted = request("2001:db8:2::1");
    untrusted.addHeader(AuthRequestRateLimitService.CLIENT_IP_HEADER, "2001:db8::2");
    assertThat(service.clientAddress(untrusted)).isEqualTo("2001:db8:2:0:0:0:0:1");
  }

  @Test
  void 전체_인터넷_CIDR와_호스트명_설정은_기동시_거절한다() {
    for (String value :
        List.of(
            "0.0.0.0/0", "::/0", "localhost", "10.0.0.1/33", "::1/129", "10.0.0.1/not-a-prefix")) {
      assertThatThrownBy(() -> service(List.of(value)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void 로그인은_IP와_계정_IP_쌍과_계정_전체의_해시만_카운터에_전달하고_대기시간을_유지한다() {
    var service = service(List.of());
    when(repository.normalizeAccount("  User@Example.COM  ")).thenReturn("user@example.com");
    when(repository.consume(any(), any(), anyLong()))
        .thenReturn(Optional.of(new Rejection("login_account", 23L, true)));
    assertThatThrownBy(() -> service.login("  User@Example.COM  ", request("203.0.113.10")))
        .isInstanceOfSatisfying(
            TooManyRequestsException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
              assertThat(exception.getRetryAfterSeconds()).isEqualTo(23L);
            });
    verify(repository).consume(address.capture(), accounts.capture(), eq(300L));
    assertThat(address.getValue())
        .usingRecursiveComparison()
        .isEqualTo(new Bucket("login_ip", SecureTokens.hash("203.0.113.10"), 100));
    assertThat(accounts.getValue())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(
            new Bucket(
                "login_account",
                SecureTokens.hash("account_ip\nuser@example.com\n203.0.113.10"),
                10,
                "login_account_ip"),
            new Bucket("login_account", SecureTokens.hash("account\nuser@example.com"), 100));
  }

  @Test
  void 이미_한도를_넘은_창의_반복_거절은_경고를_다시_남기지_않는다(CapturedOutput output) {
    var service = service(List.of());
    when(repository.normalizeAccount("user@example.com")).thenReturn("user@example.com");
    when(repository.consume(any(), any(), anyLong()))
        .thenReturn(
            Optional.of(new Rejection("login_ip", 30L, true)),
            Optional.of(new Rejection("login_ip", 29L, false)),
            Optional.of(new Rejection("login_ip", 28L, false)));

    for (int attempt = 0; attempt < 3; attempt++)
      assertThatThrownBy(() -> service.login("user@example.com", request("203.0.113.10")))
          .isInstanceOf(TooManyRequestsException.class);

    assertThat(output.getOut().split("Authentication request limited", -1)).hasSize(2);
  }

  @Test
  void 가입은_가입_한도와_창으로_센다() {
    var service = service(List.of());
    when(repository.normalizeAccount("  User@Example.COM  ")).thenReturn("user@example.com");
    service.signup("  User@Example.COM  ", request("203.0.113.10"));
    verify(repository).consume(address.capture(), accounts.capture(), eq(1800L));
    assertThat(address.getValue())
        .usingRecursiveComparison()
        .isEqualTo(new Bucket("signup_ip", SecureTokens.hash("203.0.113.10"), 20));
    assertThat(accounts.getValue())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(new Bucket("signup_account", SecureTokens.hash("user@example.com"), 3));
  }

  @Test
  void 비활성_설정은_데이터베이스를_부르지_않는다() {
    var properties =
        new AuthRateLimitProperties(
            false, 100, 10, Duration.ofMinutes(5), 20, 3, Duration.ofMinutes(30), List.of());
    var service = new AuthRequestRateLimitService(repository, properties);
    service.login("user@example.com", request("203.0.113.10"));
    verifyNoInteractions(repository);
  }

  private AuthRequestRateLimitService service(List<String> trustedProxies) {
    var properties =
        new AuthRateLimitProperties(
            true, 100, 10, Duration.ofMinutes(5), 20, 3, Duration.ofMinutes(30), trustedProxies);
    return new AuthRequestRateLimitService(repository, properties);
  }

  private static MockHttpServletRequest request(String peer) {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr(peer);
    return request;
  }
}
