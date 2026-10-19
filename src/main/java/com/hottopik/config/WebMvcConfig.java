package com.hottopik.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleSecurityInterceptor roleSecurityInterceptor;

    @Autowired
    public WebMvcConfig(RoleSecurityInterceptor roleSecurityInterceptor) {
        this.roleSecurityInterceptor = roleSecurityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleSecurityInterceptor)
                .addPathPatterns("/admin/**", "/content/**", "/operation/**", "/teacher/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/static/**", "/error");
    }
}
