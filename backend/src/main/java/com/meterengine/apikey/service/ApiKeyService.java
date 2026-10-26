package com.meterengine.apikey.service;

import com.meterengine.apikey.config.ApiKeyProperties;
import com.meterengine.apikey.entity.ApiKey;
import com.meterengine.apikey.repository.ApiKeyRepository;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.AuthenticatedCredential;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.PrincipalKind;
import com.meterengine.global.security.SecureTokens;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {

  public static final String TARGET_TYPE = "api_key";

  public static final String CREATED_ACTION = "api_key_created";

  private final ApiKeyRepository apiKeyRepository;
  private final ApiKeyProperties apiKeyProperties;
  private final AuditLogService auditLogService;

  ApiKeyService(
      ApiKeyRepository apiKeyRepository,
      ApiKeyProperties apiKeyProperties,
      AuditLogService auditLogService) {
    this.apiKeyRepository = apiKeyRepository;
    this.apiKeyProperties = apiKeyProperties;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public Optional<AuthenticatedCredential> authenticate(String rawKey) {
    if (!SecureTokens.isApiKey(rawKey)) {
      return Optional.empty();
    }
    OffsetDateTime now = OffsetDateTime.now();
    return apiKeyRepository
        .findByKeyHash(SecureTokens.hash(rawKey))
        .filter(apiKey -> apiKey.isUsableAt(now))
        .map(ApiKeyService::credentialOf);
  }

  // 도입사의 키를 만든 순서로 돌려주며 폐기했거나 만료된 키도 담는다.
  @Transactional(readOnly = true)
  public List<ApiKey> list(UUID organizationId) {
    return apiKeyRepository.findByOrganizationIdOrderByCreatedAtAscIdAsc(organizationId);
  }

  // 키를 발급하고, 교체할 옛 키가 있으면 그 키를 유예 시간 뒤에 끝나게 한다.
  @Transactional
  public IssuedApiKey create(
      UUID organizationId, String label, String mode, List<String> scopes, UUID replacedApiKeyId) {
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    if (replacedApiKeyId != null) {
      ApiKey replaced = usableApiKeyForUpdate(organizationId, replacedApiKeyId);
      replaced.expireNoLaterThan(now.plus(apiKeyProperties.replacementGrace()));
    }
    List<String> distinctScopes = scopes.stream().distinct().toList();
    String secret = SecureTokens.generate("me_" + mode + "_");
    ApiKey apiKey =
        apiKeyRepository.save(
            new ApiKey(
                UUID.randomUUID(),
                organizationId,
                mode,
                label,
                SecureTokens.hash(secret),
                SecureTokens.lastFour(secret),
                distinctScopes,
                null,
                now,
                null));
    auditLogService.record(
        organizationId,
        CREATED_ACTION,
        TARGET_TYPE,
        apiKey.getId().toString(),
        createdAuditDetail(apiKey, replacedApiKeyId));
    return new IssuedApiKey(secret, apiKey);
  }

  // 키 발급 감사 행의 detail을 만들며, 기동 시 부트스트랩도 같은 꼴을 쓴다.
  public static Map<String, Object> createdAuditDetail(ApiKey apiKey, UUID replacedApiKeyId) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("label", apiKey.getLabel());
    detail.put("mode", apiKey.getMode());
    detail.put("scopes", apiKey.getScopes());
    detail.put(
        "replaced_api_key_id", replacedApiKeyId == null ? null : replacedApiKeyId.toString());
    return detail;
  }

  // 키를 폐기하며, 이미 폐기한 키는 폐기 시각을 그대로 두고 감사 행을 더 남기지 않는다.
  @Transactional
  public void delete(UUID organizationId, UUID apiKeyId) {
    ApiKey apiKey =
        apiKeyRepository
            .findForUpdateByIdAndOrganizationId(apiKeyId, organizationId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.API_KEY_NOT_FOUND));
    if (apiKey.getRevokedAt() != null) {
      return;
    }
    apiKey.revoke(OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));
    auditLogService.record(
        organizationId, "api_key_deleted", TARGET_TYPE, apiKey.getId().toString(), Map.of());
  }

  private ApiKey usableApiKeyForUpdate(UUID organizationId, UUID apiKeyId) {
    return apiKeyRepository
        .findForUpdateByIdAndOrganizationId(apiKeyId, organizationId)
        .filter(apiKey -> apiKey.getRevokedAt() == null)
        .orElseThrow(() -> new NotFoundException(ErrorCode.API_KEY_NOT_FOUND));
  }

  private static AuthenticatedCredential credentialOf(ApiKey apiKey) {
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            PrincipalKind.API_KEY,
            apiKey.getOrganizationId(),
            apiKey.getId(),
            null,
            null,
            apiKey.getMode(),
            null,
            Set.copyOf(apiKey.getScopes()));
    return new AuthenticatedCredential(principal, apiKey.getRateLimitPerMinute());
  }
}
