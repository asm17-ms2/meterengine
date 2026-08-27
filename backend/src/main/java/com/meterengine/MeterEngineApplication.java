package com.meterengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MeterEngineApplication {

  public static void main(String[] args) {
    SpringApplication.run(MeterEngineApplication.class, args);
  }
}
