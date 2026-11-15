package com.proveyu.auth.domain;

public enum Role {
    CANDIDATE,
    RECRUITER,
    /** Legacy organization owner role (non-college organizations). College staff use the COLLEGE_* roles. */
    ORGANIZATION,
    ADMIN,
    /** Training & Placement Officer / college administrator. */
    COLLEGE_ADMIN,
    /** Placement coordinator, optionally scoped to branches. */
    COLLEGE_COORDINATOR,
    /** Read-only college staff. */
    COLLEGE_VIEWER
}
