package com.proveyu.college.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CollegeWebConfig implements WebMvcConfigurer {
    private final CollegeContextResolver collegeContextResolver;
    private final SuspendedStudentInterceptor suspendedStudentInterceptor;

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(suspendedStudentInterceptor).addPathPatterns("/api/v1/college/student/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(collegeContextResolver);
    }
}
