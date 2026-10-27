package com.lifepedia.api.config;

import com.lifepedia.api.group.GroupActivityInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final GroupActivityInterceptor groupActivity;

    public WebConfig(GroupActivityInterceptor groupActivity) {
        this.groupActivity = groupActivity;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(groupActivity).addPathPatterns("/api/groups/*/**");
    }
}
