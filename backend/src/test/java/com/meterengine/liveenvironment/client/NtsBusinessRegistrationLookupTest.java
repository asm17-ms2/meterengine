package com.meterengine.liveenvironment.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.liveenvironment.service.BusinessRegistrationStatus;
import com.meterengine.liveenvironment.service.BusinessRegistrationStatus.State;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;

class NtsBusinessRegistrationLookupTest {

  private static final String NUMBER = "1234567891";

  private HttpServer server;
  private final AtomicReference<String> lastQuery = new AtomicReference<>();
  private final AtomicReference<String> lastBody = new AtomicReference<>();
  private final AtomicReference<String> lastContentType = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String body = status("01", "계속사업자", "부가가치세 일반과세자");
  private volatile Duration delay = Duration.ZERO;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/nts-businessman/v1/status",
        exchange -> {
          lastQuery.set(exchange.getRequestURI().getRawQuery());
          lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          lastBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          try {
            Thread.sleep(delay.toMillis());
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void 계속사업자는_OPERATING과_과세유형을_돌려주고_번호를_b_no_배열로_보낸다() {
    BusinessRegistrationStatus result = lookup("plain-key").find(NUMBER);

    assertThat(result).isEqualTo(new BusinessRegistrationStatus(State.OPERATING, "부가가치세 일반과세자"));
    assertThat(lastQuery.get()).isEqualTo("serviceKey=plain-key");
    assertThat(lastContentType.get()).startsWith("application/json");
    assertThat(lastBody.get()).isEqualTo("{\"b_no\":[\"" + NUMBER + "\"]}");
  }

  @Test
  void 휴업자는_SUSPENDED를_돌려준다() {
    body = status("02", "휴업자", "부가가치세 일반과세자");

    assertThat(lookup("plain-key").find(NUMBER))
        .isEqualTo(new BusinessRegistrationStatus(State.SUSPENDED, "부가가치세 일반과세자"));
  }

  @Test
  void 폐업자는_CLOSED를_돌려준다() {
    body = status("03", "폐업자", "부가가치세 일반과세자");

    assertThat(lookup("plain-key").find(NUMBER).state()).isEqualTo(State.CLOSED);
  }

  @Test
  void 등록되지_않은_번호는_상태_코드가_비어_오고_UNREGISTERED로_돌려준다() {
    body = status("", "", "국세청에 등록되지 않은 사업자등록번호입니다.");

    assertThat(lookup("plain-key").find(NUMBER))
        .isEqualTo(new BusinessRegistrationStatus(State.UNREGISTERED, null));
  }

  @Test
  void 상태_코드가_비었는데_등록되지_않았다는_안내가_없으면_판정하지_않고_UNAVAILABLE이다() {
    body = status("", "", "");

    assertThat(lookup("plain-key").find(NUMBER))
        .isEqualTo(BusinessRegistrationStatus.unavailable());
  }

  @Test
  void 시간_안에_답하지_않으면_UNAVAILABLE이다() {
    delay = Duration.ofMillis(1500);

    assertThat(lookup("plain-key").find(NUMBER))
        .isEqualTo(BusinessRegistrationStatus.unavailable());
  }

  @Test
  void 오류_상태나_읽을_수_없는_본문이나_다른_번호의_결과는_UNAVAILABLE이다() {
    status = 500;
    body = "{\"status_code\":\"INTERNAL_ERROR\"}";
    assertThat(lookup("plain-key").find(NUMBER))
        .isEqualTo(BusinessRegistrationStatus.unavailable());

    status = 200;
    body = "<html>점검 중</html>";
    assertThat(lookup("plain-key").find(NUMBER))
        .isEqualTo(BusinessRegistrationStatus.unavailable());

    body = "{\"status_code\":\"OK\",\"data\":[]}";
    assertThat(lookup("plain-key").find(NUMBER))
        .isEqualTo(BusinessRegistrationStatus.unavailable());

    body =
        "{\"status_code\":\"OK\",\"data\":[{\"b_no\":\"1248100998\",\"b_stt_cd\":\"01\",\"tax_type\":\"부가가치세 일반과세자\"}]}";
    assertThat(lookup("plain-key").find(NUMBER))
        .isEqualTo(BusinessRegistrationStatus.unavailable());

    body = status("09", "모르는 상태", "부가가치세 일반과세자");
    assertThat(lookup("plain-key").find(NUMBER))
        .isEqualTo(BusinessRegistrationStatus.unavailable());
  }

  @Test
  void 원문_인증키는_한_번_인코딩하고_이미_인코딩된_인증키는_그대로_보낸다() {
    lookup("ab+c/d==").find(NUMBER);
    assertThat(lastQuery.get()).isEqualTo("serviceKey=ab%2Bc%2Fd%3D%3D");

    lookup("ab%2Bc%2Fd%3D%3D").find(NUMBER);
    assertThat(lastQuery.get()).isEqualTo("serviceKey=ab%2Bc%2Fd%3D%3D");
  }

  private NtsBusinessRegistrationLookup lookup(String serviceKey) {
    return new NtsBusinessRegistrationLookup(
        RestClient.builder()
            .requestFactory(
                ClientHttpRequestFactoryBuilder.jdk()
                    .build(
                        HttpClientSettings.defaults()
                            .withTimeouts(Duration.ofMillis(500), Duration.ofMillis(500))))
            .build(),
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/nts-businessman/v1"),
        serviceKey);
  }

  private static String status(String code, String name, String taxType) {
    return """
        {"status_code":"OK","match_cnt":1,"request_cnt":1,
         "data":[{"b_no":"%s","b_stt":"%s","b_stt_cd":"%s","tax_type":"%s","tax_type_cd":"01",
                  "end_dt":"","utcc_yn":"N","tax_type_change_dt":"","invoice_apply_dt":"",
                  "rbf_tax_type":"","rbf_tax_type_cd":""}]}
        """
        .formatted(NUMBER, name, code, taxType);
  }
}
