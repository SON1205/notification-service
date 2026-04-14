package com.jjino.notificationservice.global.config;

import com.jjino.notificationservice.global.common.Constants;
import com.jjino.notificationservice.global.interceptor.AbstractLoggingInterceptor;
import java.util.Optional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final Optional<AbstractLoggingInterceptor> loggingInterceptor;

    public WebMvcConfig(Optional<AbstractLoggingInterceptor> loggingInterceptor) {
        this.loggingInterceptor = loggingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        loggingInterceptor.ifPresent(interceptor ->
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/api/**")
                        .excludePathPatterns(
                                Constants.SSE_STREAM_PATH,
                                "/actuator/**"
                        )
        );
    }
}
