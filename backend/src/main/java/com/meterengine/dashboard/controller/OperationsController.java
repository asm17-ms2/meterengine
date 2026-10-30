package com.meterengine.dashboard.controller;

import com.meterengine.dashboard.dto.OperationsResponse;
import com.meterengine.dashboard.service.OperationsService;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.OperatorAccess;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class OperationsController {
  private final OperationsService operationsService;

  OperationsController(OperationsService operationsService) {
    this.operationsService = operationsService;
  }

  @OperatorAccess
  @GetMapping("/v1/operations")
  @Operation(
      summary = "운영자 현황 조회",
      description =
          "운영자 세션으로 전체 도입사와 결제, 통보 장애, DB의 예약 작업 마지막 성공 시각을 확인한다. 결제 실패는 최근 24시간이고 웹훅 실패는 현재 failed"
              + " 상태인 전송 가운데 보관하지 않은 엔드포인트의 것이다. 꺼 둔 엔드포인트의 실패는 센다.")
  public OperationsResponse getOperations() {
    return operationsService.get();
  }
}
