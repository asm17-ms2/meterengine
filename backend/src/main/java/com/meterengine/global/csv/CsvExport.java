package com.meterengine.global.csv;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

public final class CsvExport {
  private static final String BYTE_ORDER_MARK = "﻿";
  private static final String LINE_END = "\r\n";

  private CsvExport() {}

  // 스프레드시트가 수식으로 읽는 첫 글자 앞에 작은따옴표를 붙인다.
  public static String formulaSafe(String text) {
    String trimmed = text.stripLeading();
    boolean formula =
        !text.isEmpty()
            && ("=+-@\t\r\n".indexOf(text.charAt(0)) >= 0
                || !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0);
    return formula ? "'" + text : text;
  }

  public static String quote(String value) {
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  public static ResponseEntity<byte[]> response(String filename, List<List<String>> quotedRows) {
    String body =
        quotedRows.stream()
            .map(cells -> String.join(",", cells) + LINE_END)
            .collect(Collectors.joining("", BYTE_ORDER_MARK, ""));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + ".csv\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(body.getBytes(StandardCharsets.UTF_8));
  }
}
