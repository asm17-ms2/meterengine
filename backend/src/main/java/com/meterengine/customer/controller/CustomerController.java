package com.meterengine.customer.controller;

import com.meterengine.ProblemResponse;
import com.meterengine.customer.dto.CustomerListResponse;
import com.meterengine.customer.dto.CustomerResponse;
import com.meterengine.customer.dto.SaveCustomerRequest;
import com.meterengine.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객 등록/수정/삭제와 목록 조회 (MS2-155).
 *
 * <p>이 API가 생기기 전까지 고객을 만드는 통로는 시드 스크립트뿐이었다. 수집, 집계, 청구 예정액이 전부 고객을 전제로 도는데 그 고객을 화면에서 만들 수 없었다.
 *
 * <p><b>X-Organization-Id는 임시물이다.</b> 지금은 요청이 도입사를 자칭하기만 하면 통과하므로 누구나 아무 도입사를 사칭할 수 있다. MS2-126이
 * Bearer API 키 인증을 붙이면서 이 헤더를 인증 주체에서 꺼내는 형태로 바꾼다. 그때 헤더 누락은 400이 아니라 401이 되고, 없는 도입사를 가리키는 400도
 * 사라진다 (수집, 사용량, 청구 예정액 API와 같은 사정).
 *
 * <p><b>단건 조회를 만들지 않는다.</b> 고객이 가진 정보가 목록에 이미 전부 들어 있어 같은 것을 두 경로로 내게 된다. 대신 수정의 응답이 갱신된 고객 전체를
 * 담는다. 그것이 화면이 반영 결과를 확인하는 창구다.
 */
@RestController
@RequestMapping("/v1/customers")
public class CustomerController {

  private final CustomerService customerService;

  CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping
  @Operation(
      summary = "고객 목록 조회",
      description =
          """
          이 도입사의 고객을 이름 오름차순으로 전부 돌려준다. 동명이인이 있어도 순서가 흔들리지 않게
          customer_id가 두 번째 정렬 키다. 사용량 조회, 청구 예정액과 같은 순서다.
          고객이 없거나 등록되지 않은 도입사면 customers가 빈 배열이다.
          페이지를 나누지 않는다. 이 도입사의 전부가 응답의 정의이며, 사용량과 청구 예정액 응답도
          같은 고객 집합을 한 번에 담는다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "고객 목록. 한 명도 없으면 customers가 빈 배열이다"),
    // content를 주지 않으면 오류 스키마가 200의 것으로 문서에 나간다 (MS2-140 실측).
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=validation_error: X-Organization-Id가 없거나 UUID가 아니다")
  })
  public CustomerListResponse list(
      @Parameter(description = "도입사 ID. MS2-126의 Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId) {
    return CustomerListResponse.from(customerService.list(organizationId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "고객 등록",
      description =
          """
          고객을 만들고 서버가 발급한 customer_id와 함께 돌려준다. 이벤트 수집은 이 id를 실어 보낸다.
          이름 중복을 막지 않는다. 같은 이름의 계열사나 부서를 따로 관리할 수 있고, 구별은 customer_id가 한다.
          Location 헤더를 주지 않는다. 단건 조회 경로가 없어 가리킬 곳이 없다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "만들어진 고객"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description =
            """
            code=validation_error: name이 비었거나 255자를 넘거나, X-Organization-Id가 없거나 UUID가 아니다.
            code=unknown_organization: X-Organization-Id가 등록된 도입사가 아니다.
            """)
  })
  public CustomerResponse create(
      @Parameter(description = "도입사 ID. MS2-126의 Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Valid @RequestBody SaveCustomerRequest request) {
    return CustomerResponse.from(customerService.create(organizationId, request.name()));
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "고객 수정",
      description =
          """
          고객 이름을 바꾸고 갱신된 고객을 돌려준다. 단건 조회가 없으므로 이 응답이 반영 결과를 확인하는 창구다.
          PATCH가 아닌 이유는 고칠 수 있는 것이 이름 하나이고 그것이 항상 필수라, 부분 갱신이라 부를 것이 없어서다.
          필드가 늘면서 일부만 보내는 요청이 필요해지면 그때 PATCH를 따로 추가한다.
          다른 도입사 소속이거나 이미 지워진 고객은 404다. 지운 고객은 행 자체가 없어 없는 고객과 구별되지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "갱신된 고객"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=validation_error: name이 비었거나 255자를 넘거나, 헤더나 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=customer_not_found: 없거나 다른 도입사 소속이다. 둘을 구별해 답하지 않는다")
  })
  public CustomerResponse update(
      @Parameter(description = "도입사 ID. MS2-126의 Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "고칠 고객의 ID.") @PathVariable UUID id,
      @Valid @RequestBody SaveCustomerRequest request) {
    return CustomerResponse.from(customerService.rename(organizationId, id, request.name()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "고객 삭제",
      description =
          """
          고객을 지운다. 행이 실제로 사라져 목록과 사용량, 청구 예정액에서 빠지고
          그 고객 id를 실은 이벤트는 미등록 고객으로 거절된다. 되돌리는 API는 없다.
          사용량 이벤트가 한 건이라도 있으면 지우지 않고 409로 거절한다. 이벤트는 청구 근거이고
          지울 수 없어서, 고객만 지우면 그 사용량이 어느 청구서에도 오르지 않는다.
          확정된 인보이스가 있어도 409다. 확정은 되돌리지 않으므로 고객만 지우면
          발행한 청구서가 가리킬 대상을 잃는다. 사용량 이벤트가 한 건도 없는 고객도
          금액 0으로 확정된 달이 있으면 여기 걸린다. 두 경우는 code로 구분한다.
          같은 고객을 두 번 지우면 두 번째는 204가 아니라 404다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "지웠다. 본문 없음"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=validation_error: 헤더나 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=customer_not_found: 없거나 다른 도입사 소속이다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description =
            """
            code=customer_has_events: 사용량 이벤트가 있어 지울 수 없다.
            code=customer_has_invoices: 확정된 인보이스가 있어 지울 수 없다.
            둘 다 요청을 고쳐서 될 일이 아니다.
            """)
  })
  public void delete(
      @Parameter(description = "도입사 ID. MS2-126의 Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "지울 고객의 ID.") @PathVariable UUID id) {
    customerService.delete(organizationId, id);
  }
}
