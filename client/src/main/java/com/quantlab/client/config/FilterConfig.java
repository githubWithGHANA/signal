package com.quantlab.client.config;

import com.quantlab.client.filter.ValidationFilter;
import com.quantlab.signal.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {
    private final AuthService authService;

    @Autowired
    public FilterConfig(AuthService authService) {
        this.authService = authService;
    }

    @Bean
    public FilterRegistrationBean<ValidationFilter> tokenValidationFilter() {
        FilterRegistrationBean<ValidationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ValidationFilter(authService));
        registrationBean.addUrlPatterns("/ql/*");
        registrationBean.addInitParameter("exclusions","/ws/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }
}

