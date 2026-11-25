/**
 * CXO module (role {@code CXO}): college leadership asks for a dashboard in plain language and watches it being built live.
 * <ul>
 *   <li>A CXO account belongs to one college ({@code cxo_members}) and sees only that college's data. Accounts are created by a
 *       platform admin ({@code /api/v1/admin/cxo-users}); there is no sign-up. Sign-in uses the shared {@code /api/v1/auth/login}.</li>
 *   <li>The AI never writes SQL. It picks widgets from the {@code MetricCatalog} and returns a dashboard spec (JSON); the backend
 *       validates it and runs fixed, parameterized queries with the college id taken from the signed-in account
 *       ({@code CxoContext}), never from the request or the model.</li>
 *   <li>Only metric names, branch codes and batch years go to the model for planning, and only aggregated numbers for the insight
 *       text: no student names, e-mails or phone numbers leave the server.</li>
 *   <li>{@code POST /api/v1/cxo/dashboards/generate} streams Server-Sent Events: status, plan, one event per widget with its data,
 *       insight, done. Without an AI key a rule-based planner answers, so the portal works offline too.</li>
 *   <li>Saved dashboards keep the spec only; their numbers are recomputed live on every open or refresh.</li>
 * </ul>
 */
package com.proveyu.cxo;
