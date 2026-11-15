package com.proveyu.college.security;

/** SpEL expressions for @PreAuthorize on college endpoints. */
public final class CollegeAccess {
    private CollegeAccess() {}

    /** Any college staff member (TPO, coordinator, viewer). */
    public static final String READ = "hasAnyRole('COLLEGE_ADMIN','COLLEGE_COORDINATOR','COLLEGE_VIEWER')";
    /** Staff allowed to change data. */
    public static final String WRITE = "hasAnyRole('COLLEGE_ADMIN','COLLEGE_COORDINATOR')";
    /** TPO / college admin only: team, roles, policy, settings. */
    public static final String ADMIN = "hasRole('COLLEGE_ADMIN')";
    /** Students (candidate accounts) using the self-service campus portal. */
    public static final String STUDENT = "hasRole('CANDIDATE')";
}
