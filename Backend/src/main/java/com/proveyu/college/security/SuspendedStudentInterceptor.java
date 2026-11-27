package com.proveyu.college.security;

import com.proveyu.college.application.CollegeDirectory;
import com.proveyu.college.infrastructure.CollegeStudentRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/** Student portal requests of a suspended college are refused (403 COLLEGE_SUSPENDED). */
@Component
@RequiredArgsConstructor
public class SuspendedStudentInterceptor implements HandlerInterceptor {
    private final CollegeStudentRepository students;
    private final CollegeDirectory directory;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return true;
        UUID userId;
        try {
            userId = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return true; // not signed in: the security chain answers
        }
        students.findFirstByUserIdAndDeletedAtIsNull(userId).ifPresent(s -> directory.requireNotSuspended(s.getCollegeId()));
        return true;
    }
}
