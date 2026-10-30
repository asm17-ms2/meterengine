package com.meterengine.customer.controller;

import com.meterengine.customer.dto.CreateCustomerRequest;
import com.meterengine.customer.dto.CustomerRegistration;
import com.meterengine.customer.dto.CustomerResponse;
import com.meterengine.customer.dto.ListCustomersResponse;
import com.meterengine.customer.dto.UpdateCustomerRequest;
import com.meterengine.customer.service.CustomerRegistrationService;
import com.meterengine.customer.service.CustomerService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AccessScopes;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.IngestAccess;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.global.validation.VersionPrecondition;
import com.meterengine.idempotency.service.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/customers")
public class CustomerController {
  private static final String INVALID_UPDATE =
      """
      code=validation_error: name이 비었거나 255자를 넘거나 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았거나, billing_email이 이메일 형식이 아니거나(@ 뒤 도메인에 점이 있어야 한다) 254자를 넘거나, 경로의 UUID 형식이 틀렸다. If-Match가 큰따옴표로 감싼 version이 아니어도 이 코드이며 errors의 field는 If-Match다. PATCH에서 If-Match를 빼도 같다.
      """;
  private static final String UPDATE_CONFLICT =
      """
      code=customer_archived: 보관한 고객이다. 복원한 뒤 고친다.
      code=resource_version_conflict: If-Match의 version이 지금 version과 다르다. 다시 조회한 뒤 고친다.
      """;

  private final CustomerService customerService;
  private final CustomerRegistrationService customerRegistrationService;
  private final IdempotencyService idempotencyService;

  CustomerController(
      CustomerService customerService,
      CustomerRegistrationService customerRegistrationService,
      IdempotencyService idempotencyService) {
    this.customerService = customerService;
    this.customerRegistrationService = customerRegistrationService;
    this.idempotencyService = idempotencyService;
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
      @RequestParam(name = "external_id", required = false) @Size(min = 1, max = 255) @StorableText
          String externalId,
      @Parameter(
              description = "보관 상태. 생략하면 active",
              schema =
                  @Schema(
                      allowableValues = {"active", "archived", "all"},
                      defaultValue = "active"))
          @RequestParam(defaultValue = "active")
          @StorableText
          String status) {
    return ListCustomersResponse.from(
        customerRegistrationService.list(organizationId, externalId, status));
  }

  @PostMapping
  @IngestAccess
  @Operation(
      summary = "고객 등록",
      description =
          """
          고객을 등록하고 서버가 발급한 id와 함께 돌려준다. 사용량 이벤트를 보낼 때 이 id를 customer_id에 싣는다.
          external_id는 도입사 안에서 유일하다. 같은 external_id를 다시 보내면 기존 고객을 200으로 돌려주고 이름과 이메일은 바꾸지 않는다. external_id를 생략하면 매번 새 고객을 만든다. Idempotency-Key 헤더를 사용하면 같은 조직/환경/작업의 같은 요청을 재전송해도 최초 생성 응답을 돌려준다. 같은 키와 다른 본문은 409다.
          billing_email은 선택 입력이다. 확정한 인보이스의 청구 메일을 받을 주소이고, 없으면 빼거나 null로 보낸다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "등록된 고객",
        headers =
            @Header(
                name = "Idempotency-Replayed",
                description = "같은 Idempotency-Key의 최초 응답을 다시 돌려줄 때만 true로 붙는다",
                schema =
                    @Schema(
                        type = "string",
                        allowableValues = {"true"}))),
    @ApiResponse(
        responseCode = "200",
        description =
            "같은 external_id로 이미 등록한 고객. 조회(read) 권한이 없으면 id, external_id, version만 채우고 name,"
                + " billing_email, created_at, archived_at은 null이다",
        headers =
            @Header(
                name = "Idempotency-Replayed",
                description = "같은 Idempotency-Key의 최초 응답을 다시 돌려줄 때만 true로 붙는다",
                schema =
                    @Schema(
                        type = "string",
                        allowableValues = {"true"}))),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=customer_archived: 같은 external_id의 고객을 보관했다. POST /v1/customers/{id}/restorations로 복원한 뒤 다시 보낸다.
            code=idempotency_conflict: 같은 Idempotency-Key를 다른 요청 본문에 썼다.
            """),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: name이 비었거나 255자를 넘거나 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았거나, billing_email이 이메일 형식이 아니거나(@ 뒤 도메인에 점이 있어야 한다) 254자를 넘는다, Idempotency-Key가 비었거나 255자를 넘는다.
            """)
  })
  public ResponseEntity<CustomerResponse> createCustomer(
      @CurrentOrganizationId UUID organizationId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody CreateCustomerRequest request,
      @Parameter(
              description =
                  "1~255자 재시도 키. 같은 도입사와 업무 환경에서 같은 키와 같은 본문을 다시 보내면 최초 응답을 상태 코드까지 그대로 돌려준다. 이름과 billing_email을 담은 최초 응답을 기한 없이 보관하므로 그 뒤에 고치거나 보관해도 재전송 응답은 바뀌지 않는다.")
          @RequestHeader(name = "Idempotency-Key", required = false)
          @StorableText
          String idempotencyKey) {
    ResponseEntity<CustomerResponse> response =
        idempotencyService.execute(
            organizationId,
            "customer.create",
            idempotencyKey,
            request,
            CustomerResponse.class,
            () -> {
              CustomerRegistration registration =
                  customerRegistrationService.create(organizationId, request);
              return ResponseEntity.status(
                      registration.created() ? HttpStatus.CREATED : HttpStatus.OK)
                  .body(CustomerResponse.from(registration.customer()));
            });
    if (response.getStatusCode() != HttpStatus.OK
        || AccessScopes.grants(principal.scopes(), "read")) return response;
    return ResponseEntity.status(HttpStatus.OK)
        .headers(response.getHeaders())
        .body(response.getBody().identifiersOnly());
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
  public ResponseEntity<CustomerResponse> getCustomer(
      @CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    CustomerResponse customer = CustomerResponse.from(customerService.get(organizationId, id));
    return ResponseEntity.ok().eTag(Long.toString(customer.version())).body(customer);
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
        description = INVALID_UPDATE),
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
        description = UPDATE_CONFLICT)
  })
  public CustomerResponse updateCustomer(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "고칠 고객의 ID.") @PathVariable UUID id,
      @Valid @RequestBody UpdateCustomerRequest request,
      @RequestHeader(name = "If-Match", required = false) @StorableText String ifMatch) {
    return CustomerResponse.from(
        customerService.update(
            organizationId,
            id,
            request.name(),
            request.billingEmail(),
            VersionPrecondition.parse(ifMatch)));
  }

  @PatchMapping("/{id}")
  @AdminAccess
  @Operation(
      summary = "버전을 확인한 고객 수정",
      description =
          "조회한 version을 If-Match 헤더에 큰따옴표로 감싸 보낸다. 바뀐 버전이면 409이며 원래 데이터는 유지한다. 이름/이메일을 모두 보내는 조건부 수정이다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 고객"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = INVALID_UPDATE),
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
        description = UPDATE_CONFLICT)
  })
  public CustomerResponse updateConditionalCustomer(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCustomerRequest request,
      @RequestHeader("If-Match") @StorableText String ifMatch) {
    return CustomerResponse.from(
        customerService.update(
            organizationId,
            id,
            request.name(),
            request.billingEmail(),
            VersionPrecondition.parse(ifMatch)));
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
