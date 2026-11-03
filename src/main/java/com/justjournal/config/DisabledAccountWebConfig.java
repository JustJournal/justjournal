package com.justjournal.config;

import com.justjournal.ctl.DisabledAccountInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Puts {@link DisabledAccountInterceptor} in front of every address but the admin area, where the
 * administrator must still be able to find a disabled account.
 */
@Configuration
public class DisabledAccountWebConfig implements WebMvcConfigurer {

  private final DisabledAccountInterceptor disabledAccountInterceptor;

  public DisabledAccountWebConfig(final DisabledAccountInterceptor disabledAccountInterceptor) {
    this.disabledAccountInterceptor = disabledAccountInterceptor;
  }

  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    registry
        .addInterceptor(disabledAccountInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns("/admin", "/admin/**");
  }
}
