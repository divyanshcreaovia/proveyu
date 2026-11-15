package com.proveyu.college.security;

import com.proveyu.college.domain.CollegeEnums.MemberRole;

import java.util.Set;
import java.util.UUID;

/**
 * Tenant context of the authenticated college staff member.
 * Resolved exclusively from the database membership of the JWT subject;
 * never from request parameters, headers or body.
 *
 * @param branchScope empty = all branches
 */
public record CollegeContext(UUID collegeId, UUID userId, UUID memberId, MemberRole role, Set<UUID> branchScope, String actorName) {

    public boolean isAdmin() {
        return role == MemberRole.COLLEGE_ADMIN;
    }

    public boolean canAccessBranch(UUID branchId) {
        return branchScope.isEmpty() || (branchId != null && branchScope.contains(branchId));
    }
}
