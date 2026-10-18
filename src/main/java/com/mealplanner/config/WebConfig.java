package com.mealplanner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminInterceptor adminInterceptor;
    private final ReadOnlyViewInterceptor readOnlyViewInterceptor;

    private final AccessCheckInterceptor accessCheckInterceptor;

    public WebConfig(AdminInterceptor adminInterceptor, ReadOnlyViewInterceptor readOnlyViewInterceptor,
                     AccessCheckInterceptor accessCheckInterceptor) {
        this.adminInterceptor = adminInterceptor;
        this.readOnlyViewInterceptor = readOnlyViewInterceptor;
        this.accessCheckInterceptor = accessCheckInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Runs first so a removed client or admin is stopped before anything else.
        registry.addInterceptor(accessCheckInterceptor)
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/webjars/**", "/login", "/error", "/home", "/about", "/disclaimer", "/demo-login");
        registry.addInterceptor(adminInterceptor).addPathPatterns("/admin", "/admin/**");
        registry.addInterceptor(readOnlyViewInterceptor).excludePathPatterns("/admin", "/admin/**");
    }
}
