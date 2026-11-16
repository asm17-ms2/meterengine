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
      code=validation_error: name이 비었거나 255자를 넘거나 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았거나, billing_email이 이메일 형식이 아니거나(@ 뒤 도메인에 점이 있어야 합니다) 254자를 넘거나, 경로의 UUID 형식이 틀렸습니다. If-Match가 큰따옴표로 감싼 version이 아니어도 이 코드이며 errors의 field는 If-Match입니다. PATCH에서 If-Match를 빼도 같습니다.
      """;
  private static final String UPDATE_CONFLICT =
      """
      code=customer_archived: 보관한 고객입니다. 복원한 뒤 고칩니다.
      code=resource_version_conflict: If-Match의 version이 지금 version과 다릅니다. 다시 조회한 뒤 고칩니다.
      """;

  private static final String CUSTOMER_NOT_FOUND = "code=customer_not_found: 그런 고객이 없습니다.";
  private static final String IF_MATCH_FORMAT = "고객을 조회해 받은 version을 큰따옴표로 감싼 값(예: \"3\")입니다.";

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
          등록한 고객을 이름 오름차순으로 전부 돌려줍니다. 이름이 같으면 id 순입니다.
          사용량 조회, 청구 예정액 조회의 고객 순서와 같습니다.
          페이지를 나누지 않고 external_id와 status로만 거릅니다. 검색어, 여러 조건, 정렬, 페이지가 필요하면 GET /v1/customers/search를 씁니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "고객 목록입니다. 한 명도 없으면 customers가 빈 배열입니다.")
  })
  public ListCustomersResponse listCustomers(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "external_id가 이 값과 같은 고객만 돌려줍니다. 1~255자입니다.")
          @RequestParam(name = "external_id", required = false)
          @Size(min = 1, max = 255) @StorableText
          String externalId,
      @Parameter(
              description = "보관 상태입니다. 생략하면 active입니다.",
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
          고객을 등록하고 서버가 발급한 id와 함께 돌려줍니다. 사용량 이벤트를 보낼 때 이 id를 customer_id에 싣습니다.
          external_id는 같은 업무 환경 안에서 고유합니다. 같은 external_id를 다시 보내면 기존 고객을 200으로 돌려주고 이름과 이메일은 바꾸지 않습니다. external_id를 생략하면 매번 새 고객을 만듭니다. Idempotency-Key를 쓰면 같은 키와 같은 본문으로 다시 보내도 최초 응답을 돌려줍니다. 같은 키와 다른 본문은 409입니다.
          billing_email은 선택 입력인 청구 담당 이메일입니다. 저장만 하며 MeterEngine은 이 주소로 메일을 보내지 않습니다. 없으면 빼거나 null로 보냅니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "등록된 고객",
        headers =
            @Header(
                name = "Idempotency-Replayed",
                description = "같은 Idempotency-Key의 최초 응답을 다시 돌려줄 때만 true로 붙습니다.",
                schema =
                    @Schema(
                        type = "string",
                        allowableValues = {"true"}))),
    @ApiResponse(
        responseCode = "200",
        description =
            "같은 external_id로 이미 등록한 고객입니다. read(조회) 권한이 없으면 id, external_id, version만 채우고"
                + " name, billing_email, created_at, archived_at은 null입니다.",
        headers =
            @Header(
                name = "Idempotency-Replayed",
                description = "같은 Idempotency-Key의 최초 응답을 다시 돌려줄 때만 true로 붙습니다.",
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
            code=customer_archived: 같은 external_id의 고객을 보관했습니다. POST /v1/customers/{id}/restorations로 복원한 뒤 다시 보냅니다.
            code=idempotency_conflict: 같은 Idempotency-Key를 다른 요청 본문에 썼습니다.
            """),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: name이 비었거나 255자를 넘거나 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았거나, billing_email이 이메일 형식이 아니거나(@ 뒤 도메인에 점이 있어야 합니다) 254자를 넘거나, Idempotency-Key가 비었거나 255자를 넘습니다.
            """)
  })
  public ResponseEntity<CustomerResponse> createCustomer(
      @CurrentOrganizationId UUID organizationId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody CreateCustomerRequest request,
      @Parameter(
              description =
                  "1~255자 재시도 키입니다. 같은 조직과 업무 환경에서 같은 키와 같은 본문을 다시 보내면 최초 응답을 상태 코드까지"
                      + " 그대로 돌려줍니다. 이름과 billing_email을 담은 최초 응답을 기한 없이 보관하므로 그 뒤에 고치거나 보관해도 다시"
                      + " 보낸 요청의 응답은 바뀌지 않습니다.")
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
  @Operation(
      summary = "고객 조회",
      description = "고객 하나를 돌려줍니다. ETag 헤더에 version을 실으므로, 고칠 때 그 값을 그대로 If-Match에 보냅니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "고객"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = CUSTOMER_NOT_FOUND)
  })
  public ResponseEntity<CustomerResponse> getCustomer(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "조회할 고객의 ID") @PathVariable UUID id) {
    CustomerResponse customer = CustomerResponse.from(customerService.get(organizationId, id));
    return ResponseEntity.ok().eTag(Long.toString(customer.version())).body(customer);
  }

  @PutMapping("/{id}")
  @AdminAccess
  @Operation(
      summary = "고객 수정",
      description =
          """
          고객 이름과 청구 이메일을 바꾸고 바뀐 고객을 돌려줍니다.
          보낸 값으로 통째로 바꿉니다. billing_email을 빼거나 null로 보내면 청구 이메일이 지워집니다.
          If-Match는 선택입니다. 보내면 version을 확인하고, 빼면 확인 없이 덮어씁니다. 다른 요청이 고친 값을 덮어쓰지 않으려면 If-Match를 보내거나 PATCH /v1/customers/{id}를 씁니다.
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
        description = CUSTOMER_NOT_FOUND),
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
      @Parameter(description = IF_MATCH_FORMAT + " 선택이며, 보내면 지금 version과 다를 때 409입니다.")
          @RequestHeader(name = "If-Match", required = false)
          @StorableText
          String ifMatch) {
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
          "고객 이름과 청구 이메일을 보낸 값으로 통째로 바꿉니다. PUT과 같지만 If-Match가 반드시 있어야 합니다. 조회한"
              + " version을 큰따옴표로 감싸 If-Match 헤더에 보냅니다. 그 사이 다른 요청이 고객을 고쳐 version이 다르면 409이고 원래 데이터는"
              + " 그대로입니다. 다른 요청이 고친 값을 덮어쓰지 않으려면 이 경로를 쓰고, version 확인 없이 덮어써도 되면 PUT /v1/customers/{id}를 씁니다.")
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
        description = CUSTOMER_NOT_FOUND),
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
      @Parameter(description = "고칠 고객의 ID") @PathVariable UUID id,
      @Valid @RequestBody UpdateCustomerRequest request,
      @Parameter(
              description =
                  IF_MATCH_FORMAT + " 반드시 보내야 하며, 빼거나 형식이 틀리면 400, 지금 version과 다르면 409입니다.")
          @RequestHeader("If-Match")
          @StorableText
          String ifMatch) {
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
  @Operation(
      summary = "고객 복원",
      description = "보관한 고객을 되살립니다. 그 고객의 새 이벤트와 같은 external_id의 등록을 다시 받습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "복원한 고객입니다. 보관하지 않은 고객이면 그대로 돌려줍니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = CUSTOMER_NOT_FOUND)
  })
  public CustomerResponse restoreCustomer(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "복원할 고객의 ID") @PathVariable UUID id) {
    return CustomerResponse.from(customerService.restore(organizationId, id));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @AdminAccess
  @Operation(
      summary = "고객 보관",
      description =
          "고객을 보관합니다. 그 고객의 새 이벤트와 같은 external_id의 등록을 거절하고, 지난 이벤트와 청구, 결제 기록은 남깁니다."
              + " 이미 보관한 고객이면 아무것도 바꾸지 않고 204입니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "보관했습니다. 본문은 없습니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = CUSTOMER_NOT_FOUND)
  })
  public void deleteCustomer(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "보관할 고객의 ID") @PathVariable UUID id) {
    customerService.delete(organizationId, id);
  }
}
