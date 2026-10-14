package com.mealplanner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminInterceptor adminInterceptor;
    private final ReadOnlyViewInterceptor readOnlyViewInterceptor;

    public WebConfig(AdminInterceptor adminInterceptor, ReadOnlyViewInterceptor readOnlyViewInterceptor) {
        this.adminInterceptor = adminInterceptor;
        this.readOnlyViewInterceptor = readOnlyViewInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminInterceptor).addPathPatterns("/admin", "/admin/**");
        registry.addInterceptor(readOnlyViewInterceptor).excludePathPatterns("/admin", "/admin/**");
    }
}
