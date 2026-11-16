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
          "MeterEngine 운영자 로그인 세션으로 전체 조직의 현황, 라이브 결제와 웹훅 실패, 예약 작업의 마지막 성공 시각을 확인합니다."
              + " 결제 실패는 최근 24시간 동안 요청한 라이브 결제 시도이고, 웹훅 실패는 지금 failed 상태인 전송 가운데 보관하지 않은 웹훅 URL의 것입니다."
              + " 꺼 둔 웹훅 URL의 실패는 세고, 테스트 환경의 웹훅 실패는 세지 않습니다.")
  public OperationsResponse getOperations() {
    return operationsService.get();
  }
}
