package com.meterengine.dashboard.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.dashboard.dto.DashboardInsightsKey;
import com.meterengine.dashboard.dto.DashboardInsightsResponse.DailyEventCount;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import tools.jackson.databind.json.JsonMapper;

class S3DashboardInsightsStoreTest {
  private static final String BUCKET = "insights-bucket";
  private final DashboardInsightsKey key =
      new DashboardInsightsKey(UUID.randomUUID(), UUID.randomUUID(), YearMonth.of(2026, 10));
  private final InMemoryS3Client s3Client = new InMemoryS3Client();
  private final S3DashboardInsightsStore store =
      new S3DashboardInsightsStore(s3Client, BUCKET, JsonMapper.builder().build());

  @Test
  void 객체가_없으면_없음을_준다() {
    assertThat(store.find(key)).isEmpty();
  }

  @Test
  void 조직과_환경과_달로_정한_키에_JSON으로_올리고_다시_읽는다() {
    store.save(key, FileDashboardInsightsStoreTest.insights(7));

    String objectKey =
        "insights/" + key.organizationId() + "/" + key.environmentScopeId() + "/v2/2026-10.json";
    assertThat(s3Client.bodyByObject).containsOnlyKeys(BUCKET + "/" + objectKey);
    assertThat(s3Client.contentTypeByObject).containsValue("application/json");
    assertThat(store.find(key).orElseThrow().dailyEventCounts())
        .extracting(DailyEventCount::eventCount)
        .containsExactly(7L);
  }

  @Test
  void S3_호출이_실패하면_어느_객체인지_담은_예외로_알린다() {
    s3Client.failure = SdkClientException.create("connection reset");

    assertThatThrownBy(() -> store.find(key))
        .isInstanceOf(DashboardInsightsStoreException.class)
        .hasMessageContaining(key.path());
    assertThatThrownBy(() -> store.save(key, FileDashboardInsightsStoreTest.insights(1)))
        .isInstanceOf(DashboardInsightsStoreException.class)
        .hasMessageContaining(key.path());
  }

  // 저장소가 부르는 두 호출만 흉내 낸다. 나머지는 SDK의 기본 구현이 지원하지 않는다고 던진다.
  private static final class InMemoryS3Client implements S3Client {
    private final Map<String, byte[]> bodyByObject = new HashMap<>();
    private final Map<String, String> contentTypeByObject = new HashMap<>();
    private SdkClientException failure;

    @Override
    public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest request) {
      if (failure != null) throw failure;
      byte[] body = bodyByObject.get(request.bucket() + "/" + request.key());
      if (body == null) throw NoSuchKeyException.builder().message("no such key").build();
      return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), body);
    }

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
      if (failure != null) throw failure;
      String object = request.bucket() + "/" + request.key();
      try {
        bodyByObject.put(object, body.contentStreamProvider().newStream().readAllBytes());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      contentTypeByObject.put(object, request.contentType());
      return PutObjectResponse.builder().build();
    }

    @Override
    public String serviceName() {
      return "s3";
    }

    @Override
    public void close() {}
  }
}
