package com.meterengine.dashboard.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.insights")
public record DashboardInsightsProperties(
    @DefaultValue("file") Store store, Path directory, String bucket, String s3Region) {
  public enum Store {
    FILE,
    S3
  }

  public DashboardInsightsProperties {
    if (store == Store.S3 && (bucket == null || bucket.isBlank())) {
      throw new IllegalArgumentException(
          "meterengine.insights.bucket must be set when meterengine.insights.store is s3");
    }
    if (store == Store.FILE && directory == null) {
      throw new IllegalArgumentException(
          "meterengine.insights.directory must be set when meterengine.insights.store is file");
    }
  }
}
