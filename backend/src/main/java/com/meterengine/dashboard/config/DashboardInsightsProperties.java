package com.meterengine.dashboard.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("meterengine.insights")
public record DashboardInsightsProperties(Path directory) {

  public DashboardInsightsProperties {
    if (directory == null) {
      throw new IllegalArgumentException("meterengine.insights.directory must be set");
    }
  }
}
