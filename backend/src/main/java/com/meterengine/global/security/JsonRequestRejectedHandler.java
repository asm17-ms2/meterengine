package com.meterengine.global.security;

import com.meterengine.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;

public class JsonRequestRejectedHandler implements RequestRejectedHandler {

  private final ErrorResponseWriter errorResponseWriter;

  private final RequestAuditRecorder requestAuditRecorder;

  public JsonRequestRejectedHandler(ErrorResponseWriter errorResponseWriter) {
    this(errorResponseWriter, null);
  }

  public JsonRequestRejectedHandler(
      ErrorResponseWriter errorResponseWriter, RequestAuditRecorder requestAuditRecorder) {
    this.errorResponseWriter = errorResponseWriter;
    this.requestAuditRecorder = requestAuditRecorder;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      RequestRejectedException requestRejectedException)
      throws IOException {
    errorResponseWriter.write(response, ErrorCode.VALIDATION_ERROR);
    RequestLogFilter.logRejected(request);
    if (requestAuditRecorder != null
        && request.getRequestURI().startsWith("/v1/")
        && request.getAttribute(RequestAuditFilter.AUDITED_ATTRIBUTE) == null)
      requestAuditRecorder.record(
          null,
          request.getMethod(),
          "/v1/{unmatched}",
          400,
          0,
          UUID.randomUUID(),
          RequestAuditTarget.empty());
  }
}
