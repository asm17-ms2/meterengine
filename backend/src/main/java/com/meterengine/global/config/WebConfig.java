package com.meterengine.global.config;

import com.meterengine.global.security.CurrentOrganizationIdArgumentResolver;
import com.meterengine.organization.service.OrganizationService;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

  private final OrganizationService organizationService;

  WebConfig(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new CurrentOrganizationIdArgumentResolver(organizationService));
  }
}
