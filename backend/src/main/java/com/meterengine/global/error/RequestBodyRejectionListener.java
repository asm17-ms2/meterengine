package com.meterengine.global.error;

import jakarta.servlet.http.HttpServletRequest;

// 본문 검사의 400은 컨트롤러 본문 전에 끝나므로, 이 거절을 세야 하는 도메인이 여기서 알림을 받는다.
public interface RequestBodyRejectionListener {
  void onRejected(HttpServletRequest request, ErrorCode errorCode);
}
