package com.meterengine.global.config;

import com.meterengine.global.security.CurrentOrganizationIdArgumentResolver;
import com.meterengine.global.security.OrganizationAccessPolicy;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

  private final OrganizationAccessPolicy organizationAccessPolicy;

  WebConfig(OrganizationAccessPolicy organizationAccessPolicy) {
    this.organizationAccessPolicy = organizationAccessPolicy;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new CurrentOrganizationIdArgumentResolver(organizationAccessPolicy));
  }
}
