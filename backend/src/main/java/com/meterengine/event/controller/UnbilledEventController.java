package com.meterengine.event.controller;

import com.meterengine.event.dto.UnbilledEventsResponse;
import com.meterengine.event.service.UnbilledEventService;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import io.swagger.v3.oas.annotations.Operation;
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
      summary = "청구 제외와 정정 이벤트 조회",
      description =
          "미매칭 유형, 숫자가 아닌 값, 미래 시각과 음수 수량 이벤트를 센다. 음수 수량은 거절하거나 집계에서 빼지 않는다. 미래 시각은 cutoff_at 이상이며"
              + " 시각이 지나면 집계에 들어간다. 수량 판정은 미터별이라 같은 이벤트가 여러 미터에 잡힐 수 있다.")
  public UnbilledEventsResponse getUnbilledEvents(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") @FourDigitYear
          YearMonth month) {
    return unbilledEventService.get(organizationId, month);
  }
}
