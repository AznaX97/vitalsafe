package com.vitalsafe.api.config;

import com.vitalsafe.api.security.AuthThrottleFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThrottleConfig {

    // Registered as a servlet filter that runs before Spring Security (whose order is -100), so a blocked request costs nothing.
    // The multiplier is 1 everywhere except the end-to-end tests, which call these endpoints a lot from one address.
    @Bean
    public FilterRegistrationBean<AuthThrottleFilter> authThrottleFilter(@Value("${app.throttle.multiplier:1}") int multiplier) {
        FilterRegistrationBean<AuthThrottleFilter> registration = new FilterRegistrationBean<>(AuthThrottleFilter.withDefaults(multiplier));
        registration.setOrder(-200);
        return registration;
    }
}