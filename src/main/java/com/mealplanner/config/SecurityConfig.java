package com.mealplanner.config;

import com.mealplanner.service.CustomOAuth2UserService;
import com.mealplanner.service.UserProvisioningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com",
            "font-src 'self' https://cdn.jsdelivr.net https://fonts.gstatic.com",
            // Google profile pictures come from googleusercontent.com.
            "img-src 'self' data: https://*.googleusercontent.com",
            "connect-src 'self'",
            "frame-ancestors 'self'",
            "form-action 'self'",
            "base-uri 'self'",
            "object-src 'none'");

    private final CustomOAuth2UserService customOAuth2UserService;
    private final UserProvisioningService userProvisioningService;
    private final boolean h2ConsoleEnabled;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          UserProvisioningService userProvisioningService,
                          @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.userProvisioningService = userProvisioningService;
        this.h2ConsoleEnabled = h2ConsoleEnabled;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/", "/home", "/about", "/login", "/error", "/webjars/**", "/css/**", "/js/**", "/images/**", "/oauth2/**", "/login/oauth2/**", "/demo-login").permitAll();
                if (h2ConsoleEnabled) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                // Google sign-in requests the openid scope, so it goes through the
                // OIDC service. Both services run the same invite check.
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(oidcUserService()))
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/home")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                // Only our own scripts may run. 'unsafe-inline' is still needed for the inline
                // onclick handlers in the templates; the rest still blocks injected third-party code.
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=(), payment=(), usb=()")));

        if (h2ConsoleEnabled) {
            http.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));
        }

        return http.build();
    }

    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return userRequest -> {
            OidcUser oidcUser = delegate.loadUser(userRequest);
            userProvisioningService.onLogin(
                    oidcUser.getEmail(), oidcUser.getFullName(), oidcUser.getSubject(), oidcUser.getPicture());
            return oidcUser;
        };
    }
}
