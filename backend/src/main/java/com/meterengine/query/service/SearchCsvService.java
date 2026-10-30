package com.meterengine.query.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.csv.CsvExport;
import com.meterengine.query.dto.SearchPageResponse;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SearchCsvService {
  private static final TypeReference<Map<String, Object>> VALUES = new TypeReference<>() {};
  private final JsonMapper jsonMapper;

  SearchCsvService(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  public <T extends Record> ResponseEntity<byte[]> export(
      String filename, SearchPageResponse<T> page, Class<T> itemType) {
    if (page.total() > 10000)
      throw SearchParameters.invalid("export", "10000행 이하가 되도록 검색 범위를 좁혀 주세요");
    List<Map<String, Object>> rows = new ArrayList<>();
    LinkedHashSet<String> columns = new LinkedHashSet<>();
    for (Object value : page.items()) {
      Map<String, Object> row = new LinkedHashMap<>();
      flatten("", jsonMapper.readValue(jsonMapper.writeValueAsString(value), VALUES), row);
      columns.addAll(row.keySet());
      rows.add(row);
    }
    List<String> headers = columns.isEmpty() ? columns("", itemType) : new ArrayList<>(columns);
    List<List<String>> lines = new ArrayList<>();
    lines.add(headers.stream().map(CsvExport::quote).toList());
    for (Map<String, Object> row : rows) {
      List<String> cells = new ArrayList<>();
      for (String column : headers) cells.add(cell(row.get(column)));
      lines.add(cells);
    }
    return CsvExport.response(filename, lines);
  }

  private static List<String> columns(String prefix, Class<?> type) {
    List<String> result = new ArrayList<>();
    for (RecordComponent component : type.getRecordComponents()) {
      String column =
          prefix.isEmpty() ? jsonName(type, component) : prefix + "." + jsonName(type, component);
      if (component.getType().isRecord()) result.addAll(columns(column, component.getType()));
      else result.add(column);
    }
    return result;
  }

  private static String jsonName(Class<?> type, RecordComponent component) {
    try {
      JsonProperty property =
          type.getDeclaredField(component.getName()).getAnnotation(JsonProperty.class);
      return property == null || property.value().isEmpty()
          ? component.getName()
          : property.value();
    } catch (NoSuchFieldException exception) {
      return component.getName();
    }
  }

  private static void flatten(
      String prefix, Map<String, Object> source, Map<String, Object> result) {
    source.forEach(
        (key, value) -> {
          String column = prefix.isEmpty() ? key : prefix + "." + key;
          if (value instanceof Map<?, ?> nested) {
            Map<String, Object> converted = new LinkedHashMap<>();
            nested.forEach(
                (nestedKey, nestedValue) -> converted.put(nestedKey.toString(), nestedValue));
            flatten(column, converted, result);
          } else result.put(column, value);
        });
  }

  private String cell(Object value) {
    if (value == null) return CsvExport.quote("");
    if (value instanceof Number || value instanceof Boolean)
      return CsvExport.quote(value.toString());
    String text = value instanceof String string ? string : jsonMapper.writeValueAsString(value);
    return CsvExport.quote(CsvExport.formulaSafe(text));
  }
}
