package com.meterengine.dashboard.config;

import com.meterengine.dashboard.client.DashboardInsightsStore;
import com.meterengine.dashboard.client.FileDashboardInsightsStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class DashboardInsightsStoreConfig {

  @Bean
  DashboardInsightsStore dashboardInsightsStore(
      DashboardInsightsProperties properties, JsonMapper jsonMapper) {
    return new FileDashboardInsightsStore(properties.directory(), jsonMapper);
  }
}
