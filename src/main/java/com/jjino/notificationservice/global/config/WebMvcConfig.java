package com.jjino.notificationservice.global.config;

import com.jjino.notificationservice.global.common.Constants;
import com.jjino.notificationservice.global.interceptor.AbstractLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AbstractLoggingInterceptor loggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        Constants.SSE_STREAM_PATH,
                        "/actuator/**"
                );
    }
}
