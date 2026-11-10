package com.lifepedia.api.config;

import com.lifepedia.api.group.GroupActivityInterceptor;
import com.lifepedia.api.user.LastSeenTracker;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final GroupActivityInterceptor groupActivity;
    private final LastSeenTracker lastSeen;

    public WebConfig(GroupActivityInterceptor groupActivity, LastSeenTracker lastSeen) {
        this.groupActivity = groupActivity;
        this.lastSeen = lastSeen;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(lastSeen).addPathPatterns("/api/**");
        registry.addInterceptor(groupActivity).addPathPatterns("/api/groups/*/**");
    }
}
