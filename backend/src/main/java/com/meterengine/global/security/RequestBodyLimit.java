package com.meterengine.global.security;

// POST 경로 하나가 받는 본문 크기의 상한. 도메인이 빈으로 내놓으면 RequestBodyLimitFilter가 지킨다.
public record RequestBodyLimit(String path, long maxBytes) {}
