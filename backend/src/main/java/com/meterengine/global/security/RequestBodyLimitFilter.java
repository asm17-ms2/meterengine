package com.meterengine.global.security;

import com.meterengine.global.error.ContentTooLargeException;
import com.meterengine.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

// 큰 본문을 읽기 전에 413으로 끊는다. 인증 뒤에 돌아서 키가 없는 요청은 401이 먼저다.
public class RequestBodyLimitFilter extends OncePerRequestFilter {

  private static final int END_OF_STREAM = -1;

  private final Map<String, Long> maxBytesByPath;
  private final ErrorResponseWriter errorResponseWriter;

  public RequestBodyLimitFilter(
      List<RequestBodyLimit> requestBodyLimits, ErrorResponseWriter errorResponseWriter) {
    this.maxBytesByPath =
        requestBodyLimits.stream()
            .collect(Collectors.toMap(RequestBodyLimit::path, RequestBodyLimit::maxBytes));
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !HttpMethod.POST.matches(request.getMethod())
        || !maxBytesByPath.containsKey(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long maxBytes = maxBytesByPath.get(request.getRequestURI());
    if (request.getContentLengthLong() > maxBytes) {
      errorResponseWriter.write(response, ErrorCode.REQUEST_BODY_TOO_LARGE);
      return;
    }
    filterChain.doFilter(new LimitedBodyRequest(request, maxBytes), response);
  }

  // Content-Length 없이 나눠 보내는 본문은 길이를 미리 모르므로 읽으면서 센다.
  private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

    private final long maxBytes;

    private LimitedBodyRequest(HttpServletRequest request, long maxBytes) {
      super(request);
      this.maxBytes = maxBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      return new LimitedInputStream(super.getInputStream(), maxBytes);
    }
  }

  private static final class LimitedInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long maxBytes;
    private long readBytes;

    private LimitedInputStream(ServletInputStream delegate, long maxBytes) {
      this.delegate = delegate;
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      int value = delegate.read();
      if (value != END_OF_STREAM) {
        count(1);
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int read = delegate.read(buffer, offset, length);
      if (read != END_OF_STREAM) {
        count(read);
      }
      return read;
    }

    private void count(int bytes) {
      readBytes += bytes;
      if (readBytes > maxBytes) {
        throw new ContentTooLargeException(ErrorCode.REQUEST_BODY_TOO_LARGE);
      }
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      delegate.setReadListener(readListener);
    }
  }
}
