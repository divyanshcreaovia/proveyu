package com.proveyu.admin.web;

import org.springframework.security.core.Authentication;

import java.util.UUID;

final class AdminSupport {
    private AdminSupport() {}

    static UUID adminId(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
