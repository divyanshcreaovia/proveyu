/**
 * Platform admin module (role {@code ADMIN}): dashboard, colleges (approve, reject, suspend), every user (search, block, password
 * reset), platform admins, AI interview payments, proctoring across colleges, broadcasts, settings and the admin audit log.
 * <ul>
 *   <li>There is no admin sign-up. The first admin comes from ADMIN_BOOTSTRAP_EMAIL / _PASSWORD ({@code AdminBootstrap});
 *       every further admin is created by an admin.</li>
 *   <li>Settings live in {@code shared.settings.PlatformSettings} (read by the interview and prep modules); actions of other modules
 *       reach the audit log as {@code shared.audit.AdminActionEvent}.</li>
 *   <li>Read-only numbers come straight from the tables ({@code AdminDashboardService}), so other modules need no admin code.</li>
 * </ul>
 */
package com.proveyu.admin;
