package com.meterengine.dashboard.dto;

import java.time.YearMonth;
import java.util.UUID;

/** 인사이트 한 벌을 가리킨다. 조직, 업무 환경, 달마다 한 벌이다. */
public record DashboardInsightsKey(UUID organizationId, UUID environmentScopeId, YearMonth month) {
  /** 저장소 안에서 이 한 벌이 놓이는 자리. 파일 경로와 S3 키가 같은 꼴을 쓴다. */
  public String path() {
    return organizationId + "/" + environmentScopeId + "/" + month + ".json";
  }
}
