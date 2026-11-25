package com.proveyu.cxo.security;

import java.util.UUID;

/**
 * The signed-in CXO and their college. Resolved only from the database membership of the JWT subject, never from the request,
 * so every query in this module is scoped to {@link #collegeId()}.
 */
public record CxoContext(UUID collegeId, UUID userId, UUID memberId, String fullName, String email, String designation, String collegeName) {}
