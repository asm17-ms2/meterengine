package com.meterengine.apikey.dto;

import com.meterengine.apikey.entity.ApiKey;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record UpdateApiKeyScopesRequest(
    @ArraySchema(
            arraySchema = @Schema(description = "하나 이상 선택한다. admin은 ingest와 read를 포함한다"),
            schema = @Schema(allowableValues = {"ingest", "read", "admin"}))
        @NotEmpty List<@NotNull @StorableText @Pattern(regexp = ApiKey.SCOPE_PATTERN) String> scopes) {}
