package com.meterengine.dashboard.client;

import com.meterengine.dashboard.dto.DashboardInsightsKey;
import com.meterengine.dashboard.dto.DashboardInsightsResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** 서버의 디렉터리에 둔다. S3가 없는 로컬 개발과 시험에서 쓴다. */
public class FileDashboardInsightsStore implements DashboardInsightsStore {
  private final Path directory;
  private final JsonMapper jsonMapper;

  public FileDashboardInsightsStore(Path directory, JsonMapper jsonMapper) {
    this.directory = directory;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<DashboardInsightsResponse> find(DashboardInsightsKey key) {
    Path file = directory.resolve(key.path());
    if (!Files.exists(file)) return Optional.empty();
    try {
      return Optional.of(
          jsonMapper.readValue(Files.readAllBytes(file), DashboardInsightsResponse.class));
    } catch (IOException | JacksonException e) {
      throw new DashboardInsightsStoreException("인사이트 파일을 읽지 못했습니다: " + file, e);
    }
  }

  @Override
  public void save(DashboardInsightsKey key, DashboardInsightsResponse insights) {
    Path file = directory.resolve(key.path());
    try {
      Files.createDirectories(file.getParent());
      // 쓰다 만 파일을 다른 요청이 읽지 않도록 옆에 쓴 뒤 한 번에 바꿔 놓는다
      Path written = Files.createTempFile(file.getParent(), key.month().toString(), ".tmp");
      Files.write(written, jsonMapper.writeValueAsBytes(insights));
      Files.move(
          written, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | JacksonException e) {
      throw new DashboardInsightsStoreException("인사이트 파일을 쓰지 못했습니다: " + file, e);
    }
  }
}
