package com.proveyu.cxo.security;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.college.domain.CollegeEnums.ApprovalStatus;
import com.proveyu.college.domain.CollegeEnums.MemberStatus;
import com.proveyu.college.domain.CollegeProfile;
import com.proveyu.college.infrastructure.CollegeProfileRepository;
import com.proveyu.cxo.domain.CxoMember;
import com.proveyu.cxo.infrastructure.CxoMemberRepository;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/** Resolves {@link CxoContext} for controller methods from the signed-in user's ACTIVE CXO membership. */
@Component
@RequiredArgsConstructor
public class CxoContextResolver implements HandlerMethodArgumentResolver {

    private final CxoMemberRepository members;
    private final CollegeProfileRepository profiles;
    private final UserRepository users;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CxoContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request, WebDataBinderFactory binderFactory) {
        return resolve(SecurityContextHolder.getContext().getAuthentication());
    }

    public CxoContext resolve(Authentication auth) {
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            throw new DomainException("Authentication required", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        UUID userId;
        try {
            userId = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid authentication principal", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        boolean cxoToken = auth.getAuthorities().stream().anyMatch(a -> ("ROLE_" + Role.CXO.name()).equals(a.getAuthority()));
        if (!cxoToken) throw new DomainException("This area is for college leadership accounts", HttpStatus.FORBIDDEN, "NOT_CXO");

        CxoMember m = members.findByUserId(userId)
                .orElseThrow(() -> new DomainException("This account is not linked to a college", HttpStatus.FORBIDDEN, "NOT_CXO_MEMBER"));
        if (m.getStatus() != MemberStatus.ACTIVE) {
            throw new DomainException("Your leadership access has been revoked", HttpStatus.FORBIDDEN, "ACCESS_REVOKED");
        }
        CollegeProfile college = profiles.findById(m.getCollegeId())
                .orElseThrow(() -> new DomainException("College not found", HttpStatus.FORBIDDEN, "COLLEGE_NOT_FOUND"));
        if (college.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new DomainException("Your college is not active on ProveYu right now. Contact support.", HttpStatus.FORBIDDEN, "COLLEGE_NOT_ACTIVE");
        }
        User u = users.findById(userId).orElseThrow(() -> new DomainException("Account not found", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"));
        return new CxoContext(m.getCollegeId(), userId, m.getId(), u.getFullName(), u.getEmail(), m.getDesignation(), college.getCollegeName());
    }
}
