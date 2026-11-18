package com.proveyu.shared.audit;

import java.util.UUID;

/**
 * Something a platform admin did (approve a college, approve a payment, upload a test, ...). Modules publish it with Spring's
 * ApplicationEventPublisher; the admin module stores it in the admin audit log. This keeps modules independent of the admin module.
 */
public record AdminActionEvent(UUID adminId, String action, String targetType, String targetId, String summary) {}
