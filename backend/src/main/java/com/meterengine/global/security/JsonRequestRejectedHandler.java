package com.meterengine.global.security;

import com.meterengine.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;

public class JsonRequestRejectedHandler implements RequestRejectedHandler {

  private final ErrorResponseWriter errorResponseWriter;

  public JsonRequestRejectedHandler(ErrorResponseWriter errorResponseWriter) {
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      RequestRejectedException requestRejectedException)
      throws IOException {
    errorResponseWriter.write(response, ErrorCode.VALIDATION_ERROR);
    RequestLogFilter.logRejected(request);
  }
}
