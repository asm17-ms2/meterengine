package com.meterengine.event.dto;

import java.util.Map;

// 토픽에서 읽은 레코드 하나. 본문을 풀지 못해도 받은 그대로 DLQ에 옮길 수 있게 바이트로 들고 다닌다.
public record EventIngestRecord(String key, byte[] value, Map<String, byte[]> headers) {}
