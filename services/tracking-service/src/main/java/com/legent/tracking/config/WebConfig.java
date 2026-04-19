package com.legent.tracking.config;

import com.legent.security.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @org.springframework.lang.NonNull
    private final TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(@org.springframework.lang.NonNull InterceptorRegistry registry) {
        // Exclude /o.gif and /c because they are public tracking links that encode tenant context inside the URL params
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                    "/api/v1/health/**", 
                    "/actuator/**",
                    "/api/v1/tracking/o.gif",
                    "/api/v1/tracking/c"
                );
    }
}
