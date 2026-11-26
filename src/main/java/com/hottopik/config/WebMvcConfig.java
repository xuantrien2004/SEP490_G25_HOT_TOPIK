package com.hottopik.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleSecurityInterceptor roleSecurityInterceptor;
    private final SubscriptionSessionInterceptor subscriptionSessionInterceptor;

    @Autowired
    public WebMvcConfig(RoleSecurityInterceptor roleSecurityInterceptor,
                        SubscriptionSessionInterceptor subscriptionSessionInterceptor) {
        this.roleSecurityInterceptor = roleSecurityInterceptor;
        this.subscriptionSessionInterceptor = subscriptionSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleSecurityInterceptor)
                .addPathPatterns("/admin/**", "/content/**", "/operation/**", "/teacher/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/static/**", "/error");
        // Module gói học: cập nhật quyền Premium của học viên theo database ở mỗi request.
        registry.addInterceptor(subscriptionSessionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/static/**", "/error", "/favicon.ico",
                        "/payment/vnpay/ipn");
    }
}
