package com.meterengine.global.config;

// 같은 이미지를 띄우는 서버가 맡는 일. all은 한 프로세스가 전부 맡는 로컬과 지금의 서버 구성이다.
public enum ServerRole {
  ALL,
  INGEST,
  CONSUMER,
  API;

  public static final String PROPERTY = "meterengine.role";
}
