package com.meterengine.customer.controller;

import com.meterengine.customer.dto.CreateCustomerRequest;
import com.meterengine.customer.dto.CustomerResponse;
import com.meterengine.customer.dto.ListCustomersResponse;
import com.meterengine.customer.dto.UpdateCustomerRequest;
import com.meterengine.customer.service.CustomerService;
import com.meterengine.global.error.ErrorResponse;
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
          등록한 고객을 이름 오름차순으로 전부 돌려준다. 이름이 같으면 id 순이다.
          사용량 조회, 청구 예정액 조회의 고객 순서와 같다.
          X-Organization-Id가 등록되지 않은 도입사여도 오류가 아니라 빈 배열이다.
          페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "고객 목록. 한 명도 없으면 customers가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: X-Organization-Id가 없거나 UUID가 아니다")
  })
  public ListCustomersResponse listCustomers(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId) {
    return ListCustomersResponse.from(customerService.list(organizationId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "고객 등록",
      description =
          """
          고객을 등록하고 서버가 발급한 id와 함께 돌려준다. 사용량 이벤트를 보낼 때 이 id를 customer_id에 싣는다.
          이름이 같은 고객을 여럿 등록할 수 있다. 구별은 id로 한다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록된 고객"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: name이 비었거나 255자를 넘거나, X-Organization-Id가 없거나 UUID가 아니다.
            code=unknown_organization: X-Organization-Id가 등록된 도입사가 아니다.
            """)
  })
  public CustomerResponse createCustomer(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Valid @RequestBody CreateCustomerRequest request) {
    return CustomerResponse.from(customerService.create(organizationId, request.name()));
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "고객 수정",
      description =
          """
          고객 이름을 바꾸고 바뀐 고객을 돌려준다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 고객"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: name이 비었거나 255자를 넘거나, 헤더나 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: 그런 고객이 없다")
  })
  public CustomerResponse updateCustomer(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "고칠 고객의 ID.") @PathVariable UUID id,
      @Valid @RequestBody UpdateCustomerRequest request) {
    return CustomerResponse.from(customerService.update(organizationId, id, request.name()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "고객 삭제",
      description =
          """
          고객을 지운다. 지운 고객은 목록과 사용량, 청구 예정액에서 빠지고
          그 id로 보낸 이벤트는 404(customer_not_found)로 거절된다. 지운 고객은 되돌릴 수 없다.
          사용량 이벤트가 한 건이라도 있는 고객은 지울 수 없고 409다.
          이미 지운 고객을 다시 지우면 404다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "지웠다. 본문 없음"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 헤더나 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: 그런 고객이 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_has_events: 사용량 이벤트가 있는 고객이라 지울 수 없다")
  })
  public void deleteCustomer(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "지울 고객의 ID.") @PathVariable UUID id) {
    customerService.delete(organizationId, id);
  }
}
