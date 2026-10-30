package com.meterengine.global.config;

import com.meterengine.global.security.BusinessEnvironmentInterceptor;
import com.meterengine.global.security.CurrentOrganizationIdArgumentResolver;
import com.meterengine.global.security.OrganizationAccessPolicy;
import com.meterengine.global.security.OrganizationEnvironments;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

  private final OrganizationAccessPolicy organizationAccessPolicy;
  private final OrganizationEnvironments organizationEnvironments;

  WebConfig(
      OrganizationAccessPolicy organizationAccessPolicy,
      OrganizationEnvironments organizationEnvironments) {
    this.organizationAccessPolicy = organizationAccessPolicy;
    this.organizationEnvironments = organizationEnvironments;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new BusinessEnvironmentInterceptor(organizationEnvironments))
        .addPathPatterns("/v1/**");
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new CurrentOrganizationIdArgumentResolver(organizationAccessPolicy));
  }
}
