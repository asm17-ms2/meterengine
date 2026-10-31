package com.meterengine;

import com.meterengine.global.migration.SchemaMigrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MeterEngineApplication {

  public static void main(String[] args) {
    if (SchemaMigrator.run(args, System.getenv())) return;
    SpringApplication.run(MeterEngineApplication.class, args);
  }
}
