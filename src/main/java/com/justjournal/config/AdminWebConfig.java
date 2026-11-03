package com.justjournal.config;

import com.justjournal.ctl.admin.AdminInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts {@link AdminInterceptor} in front of the admin area. */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

  private final AdminInterceptor adminInterceptor;

  public AdminWebConfig(final AdminInterceptor adminInterceptor) {
    this.adminInterceptor = adminInterceptor;
  }

  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    registry.addInterceptor(adminInterceptor).addPathPatterns("/admin", "/admin/**");
  }
}
