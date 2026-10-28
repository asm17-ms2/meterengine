package com.meterengine.customer.controller;

import com.meterengine.customer.dto.CreateCustomerRequest;
import com.meterengine.customer.dto.CustomerResponse;
import com.meterengine.customer.dto.ListCustomersResponse;
import com.meterengine.customer.dto.UpdateCustomerRequest;
import com.meterengine.customer.service.CustomerService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.IngestAccess;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.StorableText;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  @ReadAccess
  @Operation(
      summary = "고객 목록 조회",
      description =
          """
          등록한 고객을 이름 오름차순으로 전부 돌려준다. 이름이 같으면 id 순이다.
          사용량 조회, 청구 예정액 조회의 고객 순서와 같다.
          페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "고객 목록. 한 명도 없으면 customers가 빈 배열이다")
  })
  public ListCustomersResponse listCustomers(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description = "보관 상태. 생략하면 active",
              schema =
                  @Schema(
                      allowableValues = {"active", "archived", "all"},
                      defaultValue = "active"))
          @RequestParam(defaultValue = "active")
          @StorableText
          String status) {
    return ListCustomersResponse.from(customerService.list(organizationId, status));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @IngestAccess
  @Operation(
      summary = "고객 등록",
      description =
          """
          고객을 등록하고 서버가 발급한 id와 함께 돌려준다. 사용량 이벤트를 보낼 때 이 id를 customer_id에 싣는다.
          이름이 같은 고객을 여럿 등록할 수 있다. 구별은 id로 한다.
          billing_email은 선택 입력이다. 확정한 인보이스의 청구 메일을 받을 주소이고, 없으면 빼거나 null로 보낸다.
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
            code=validation_error: name이 비었거나 255자를 넘거나 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았거나, billing_email이 이메일 형식이 아니거나(@ 뒤 도메인에 점이 있어야 한다) 254자를 넘는다.
            """)
  })
  public CustomerResponse createCustomer(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody CreateCustomerRequest request) {
    return CustomerResponse.from(
        customerService.create(organizationId, request.name(), request.billingEmail()));
  }

  @GetMapping("/{id}")
  @ReadAccess
  @Operation(summary = "고객 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "고객"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: 그런 고객이 없다")
  })
  public CustomerResponse getCustomer(
      @CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    return CustomerResponse.from(customerService.get(organizationId, id));
  }

  @PutMapping("/{id}")
  @AdminAccess
  @Operation(
      summary = "고객 수정",
      description =
          """
          고객 이름과 청구 이메일을 바꾸고 바뀐 고객을 돌려준다.
          보낸 값으로 통째로 바꾼다. billing_email을 빼거나 null로 보내면 청구 이메일이 지워진다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 고객"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: name이 비었거나 255자를 넘거나 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았거나, billing_email이 이메일 형식이 아니거나(@ 뒤 도메인에 점이 있어야 한다) 254자를 넘거나, 경로의 UUID 형식이 틀렸다"),
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
        description = "code=customer_archived: 보관한 고객이다. 복원한 뒤 고친다")
  })
  public CustomerResponse updateCustomer(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "고칠 고객의 ID.") @PathVariable UUID id,
      @Valid @RequestBody UpdateCustomerRequest request) {
    return CustomerResponse.from(
        customerService.update(organizationId, id, request.name(), request.billingEmail()));
  }

  @PostMapping("/{id}/restorations")
  @AdminAccess
  @Operation(summary = "고객 복원")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "복원한 고객. 보관하지 않은 고객이면 그대로 돌려준다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: 그런 고객이 없다")
  })
  public CustomerResponse restoreCustomer(
      @CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    return CustomerResponse.from(customerService.restore(organizationId, id));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @AdminAccess
  @Operation(
      summary = "고객 보관",
      description = "새 수집과 신규 등록을 중단한다. 기존 이벤트, 청구, 결제 이력은 유지한다. 반복 요청은 204다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "보관했다. 본문 없음"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: 그런 고객이 없다")
  })
  public void deleteCustomer(@CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    customerService.delete(organizationId, id);
  }
}
