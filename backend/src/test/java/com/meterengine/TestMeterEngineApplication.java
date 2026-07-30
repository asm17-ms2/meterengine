package com.meterengine;

import org.springframework.boot.SpringApplication;

public class TestMeterEngineApplication {

  public static void main(String[] args) {
    SpringApplication.from(MeterEngineApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
