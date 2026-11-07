package com.meterengine.event.client;

public class IngestWarmupException extends RuntimeException {

  private static final int BODY_EXCERPT_LENGTH = 200;

  IngestWarmupException(String path, int statusCode, String body) {
    super("POST " + path + " returned " + statusCode + ": " + excerpt(body));
  }

  IngestWarmupException(String path, Exception cause) {
    super("POST " + path + " failed", cause);
  }

  private static String excerpt(String body) {
    return body.length() <= BODY_EXCERPT_LENGTH ? body : body.substring(0, BODY_EXCERPT_LENGTH);
  }
}
