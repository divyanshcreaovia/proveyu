package com.proveyu.shared.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class AppProperties {
    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    /** Local development only: echo OTP / verification / invite tokens back in responses. */
    @Value("${app.dev.expose-tokens:false}")
    private boolean exposeTokens;

    @Value("${college.require-admin-approval:true}")
    private boolean collegeApprovalRequired;
}
