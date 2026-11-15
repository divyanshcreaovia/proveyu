package com.proveyu.college.security;

import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.college.domain.CollegeEnums.ApprovalStatus;
import com.proveyu.college.domain.CollegeEnums.MemberStatus;
import com.proveyu.college.domain.CollegeMember;
import com.proveyu.college.domain.CollegeProfile;
import com.proveyu.college.infrastructure.CollegeMemberRepository;
import com.proveyu.college.infrastructure.CollegeProfileRepository;
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

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves {@link CollegeContext} for controller methods from the authenticated user's
 * ACTIVE college membership. Any collegeId sent by the client is ignored.
 */
@Component
@RequiredArgsConstructor
public class CollegeContextResolver implements HandlerMethodArgumentResolver {

    private final CollegeMemberRepository memberRepository;
    private final CollegeProfileRepository profileRepository;
    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CollegeContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request, WebDataBinderFactory binderFactory) {
        boolean allowPending = parameter.getMethod() != null && parameter.getMethod().isAnnotationPresent(AllowPendingCollege.class);
        return resolve(SecurityContextHolder.getContext().getAuthentication(), allowPending);
    }

    public CollegeContext resolve(Authentication authentication, boolean allowPending) {
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new DomainException("Authentication required", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid authentication principal", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        CollegeMember member = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new DomainException("You are not a member of any college", HttpStatus.FORBIDDEN, "NOT_COLLEGE_MEMBER"));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new DomainException("Your college access has been revoked", HttpStatus.FORBIDDEN, "ACCESS_REVOKED");
        }
        // A role change makes older tokens stale: force re-login so @PreAuthorize sees the new role.
        String tokenRole = authentication.getAuthorities().stream().findFirst().map(a -> a.getAuthority().replace("ROLE_", "")).orElse("");
        if (!member.getRole().name().equals(tokenRole)) {
            throw new DomainException("Your role has changed. Please sign in again.", HttpStatus.UNAUTHORIZED, "SESSION_STALE");
        }
        if (!allowPending) {
            CollegeProfile profile = profileRepository.findById(member.getCollegeId())
                    .orElseThrow(() -> new DomainException("College profile not found", HttpStatus.FORBIDDEN, "COLLEGE_NOT_FOUND"));
            if (profile.getApprovalStatus() != ApprovalStatus.APPROVED) {
                throw new DomainException("Your college is awaiting approval by the PROVEYU team", HttpStatus.FORBIDDEN,
                        profile.getApprovalStatus() == ApprovalStatus.REJECTED ? "COLLEGE_REJECTED" : "COLLEGE_PENDING_APPROVAL");
            }
        }
        String name = userRepository.findById(userId).map(u -> u.getFullName()).orElse("Unknown");
        return new CollegeContext(member.getCollegeId(), userId, member.getId(), member.getRole(), parseScope(member.getBranchScope()), name);
    }

    public static Set<UUID> parseScope(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
    }
}
