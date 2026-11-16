package com.meterengine.event.controller;

import com.meterengine.event.dto.UnbilledEventsResponse;
import com.meterengine.event.service.UnbilledEventService;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class UnbilledEventController {
  private final UnbilledEventService unbilledEventService;

  UnbilledEventController(UnbilledEventService unbilledEventService) {
    this.unbilledEventService = unbilledEventService;
  }

  @GetMapping("/v1/unbilled-events")
  @ReadAccess
  @Operation(
      summary = "과금 제외 사용량 조회",
      description =
          "조회한 달에 발생한 이벤트 가운데 과금에서 빠지거나 확인이 필요한 이벤트를 항목별로 셉니다."
              + " 항목은 연결할 미터 없음(unmatched_type), 수량을 읽을 수 없음(non_numeric_value), 발생 시각이 미래인 이벤트(future_timestamp), 수량이 음수인 이벤트(negative_quantity)입니다."
              + " 수량이 음수인 이벤트는 거절하거나 집계에서 빼지 않습니다. 발생 시각이 미래인 이벤트는 occurred_at이 cutoff_at 이후인 이벤트이며, 그 시각이 지나면 집계에 듭니다."
              + " 수량 판정은 미터마다 하므로 같은 이벤트가 여러 미터에 잡힐 수 있습니다. 수신 시각(received_at)은 보지 않습니다.")
  public UnbilledEventsResponse getUnbilledEvents(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "조회할 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다.")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month) {
    return unbilledEventService.get(organizationId, month);
  }
}
