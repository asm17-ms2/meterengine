package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 외부에 노출되는 JSON 이름이 snake_case 규약을 지키는지 검사한다 (ADR 0007).
 *
 * <p>DTO를 리플렉션으로 훑지 않고 springdoc이 만든 문서를 본다. 검사 대상이 곧 실제 API 표면이라, 새 컨트롤러나 DTO가 생겨도 등록 없이 포함된다. 반대로
 * 패키지를 훑는 방식은 새 패키지가 생기면 조용히 빠진다.
 *
 * <p>잡는 것은 케이스 규약 위반뿐이다. 한 단어 필드의 오타(properties를 props로)나 응답 코드 누락 같은 명세 불일치는 잡지 못한다. 그건 정본
 * `docs/api/openapi.yaml`과의 대조로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiNamingConventionTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void 노출되는_이름에_대문자가_없다() throws Exception {
    Map<String, Object> document = fetchApiDocs();

    List<String> violations = new ArrayList<>();
    violations.addAll(schemaPropertyViolations(document));
    violations.addAll(parameterNameViolations(document));

    assertThat(violations)
        .as("외부로 나가는 이름은 DTO에 @JsonProperty로 snake_case를 명시한다 (ADR 0007)")
        .isEmpty();
  }

  private Map<String, Object> fetchApiDocs() throws Exception {
    String json =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  private static List<String> schemaPropertyViolations(Map<String, Object> document) {
    List<String> violations = new ArrayList<>();
    asMap(asMap(document.get("components")).get("schemas"))
        .forEach(
            (schemaName, schema) ->
                asMap(asMap(schema).get("properties")).keySet().stream()
                    .filter(OpenApiNamingConventionTest::hasUpperCase)
                    .forEach(property -> violations.add(schemaName + "." + property)));
    return violations;
  }

  /** 쿼리/헤더/경로 파라미터 이름도 같은 규약을 따른다. */
  private static List<String> parameterNameViolations(Map<String, Object> document) {
    List<String> violations = new ArrayList<>();
    asMap(document.get("paths"))
        .forEach(
            (path, pathItem) ->
                asMap(pathItem)
                    .forEach(
                        (method, operation) -> {
                          if (asMap(operation).get("parameters") instanceof List<?> parameters) {
                            parameters.stream()
                                .map(parameter -> String.valueOf(asMap(parameter).get("name")))
                                .filter(OpenApiNamingConventionTest::hasUpperCase)
                                .forEach(
                                    name -> violations.add(method + " " + path + " 파라미터 " + name));
                          }
                        }));
    return violations;
  }

  private static boolean hasUpperCase(String name) {
    return !name.equals(name.toLowerCase(Locale.ROOT));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }
}
