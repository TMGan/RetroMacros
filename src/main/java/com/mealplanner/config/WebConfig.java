package com.mealplanner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminInterceptor adminInterceptor;
    private final ReadOnlyViewInterceptor readOnlyViewInterceptor;
    private final AccessCheckInterceptor accessCheckInterceptor;
    private final ChallengeAccessInterceptor challengeAccessInterceptor;

    public WebConfig(AdminInterceptor adminInterceptor, ReadOnlyViewInterceptor readOnlyViewInterceptor,
                     AccessCheckInterceptor accessCheckInterceptor, ChallengeAccessInterceptor challengeAccessInterceptor) {
        this.adminInterceptor = adminInterceptor;
        this.readOnlyViewInterceptor = readOnlyViewInterceptor;
        this.accessCheckInterceptor = accessCheckInterceptor;
        this.challengeAccessInterceptor = challengeAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Runs first so a removed client or admin is stopped before anything else.
        registry.addInterceptor(accessCheckInterceptor)
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/webjars/**", "/login", "/error", "/home", "/about", "/disclaimer", "/demo-login");
        registry.addInterceptor(adminInterceptor).addPathPatterns("/admin", "/admin/**");
        registry.addInterceptor(readOnlyViewInterceptor).excludePathPatterns("/admin", "/admin/**");
        // After a client's challenge access ends, everything outside the admin area is read-only.
        registry.addInterceptor(challengeAccessInterceptor).excludePathPatterns("/admin", "/admin/**");
    }
}
