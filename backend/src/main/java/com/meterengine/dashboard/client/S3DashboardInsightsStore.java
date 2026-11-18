package com.meterengine.dashboard.client;

import com.meterengine.dashboard.dto.DashboardInsightsKey;
import com.meterengine.dashboard.dto.DashboardInsightsResponse;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** 버킷 하나에 조직, 업무 환경, 달마다 객체 하나로 둔다. */
public class S3DashboardInsightsStore implements DashboardInsightsStore {
  static final String KEY_PREFIX = "insights/";
  private static final String CONTENT_TYPE = "application/json";
  private final S3Client s3Client;
  private final String bucket;
  private final JsonMapper jsonMapper;

  public S3DashboardInsightsStore(S3Client s3Client, String bucket, JsonMapper jsonMapper) {
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<DashboardInsightsResponse> find(DashboardInsightsKey key) {
    String objectKey = KEY_PREFIX + key.path();
    try {
      byte[] body =
          s3Client.getObjectAsBytes(request -> request.bucket(bucket).key(objectKey)).asByteArray();
      return Optional.of(jsonMapper.readValue(body, DashboardInsightsResponse.class));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    } catch (SdkException | JacksonException e) {
      throw new DashboardInsightsStoreException("인사이트 객체를 읽지 못했습니다: " + objectKey, e);
    }
  }

  @Override
  public void save(DashboardInsightsKey key, DashboardInsightsResponse insights) {
    String objectKey = KEY_PREFIX + key.path();
    try {
      s3Client.putObject(
          request -> request.bucket(bucket).key(objectKey).contentType(CONTENT_TYPE),
          RequestBody.fromBytes(jsonMapper.writeValueAsBytes(insights)));
    } catch (SdkException | JacksonException e) {
      throw new DashboardInsightsStoreException("인사이트 객체를 쓰지 못했습니다: " + objectKey, e);
    }
  }
}
