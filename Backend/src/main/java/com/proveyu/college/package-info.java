/**
 * College module: the placement cell (TPO, coordinators, viewers) and the company Drive Room.
 * <ul>
 *   <li>{@code domain}, {@code infrastructure}: students, companies, drives, rounds, interviews, offers, team, audit (the data
 *       the student module also reads).</li>
 *   <li>{@code application}: the services, including the eligibility and placement policy engine.</li>
 *   <li>{@code security}: the college context and role checks.</li>
 *   <li>{@code web}: the {@code /api/v1/college/**} endpoints, the company Drive Room ({@code /api/v1/recruiter/campus/**})
 *       and the platform admin's college approval ({@code /api/v1/admin/colleges}).</li>
 *   <li>{@code legacy}: entities kept from the removed assessment modules that the college still reads (organization on
 *       registration, candidate profile and verified scores for the student passport and the skill-gap report).</li>
 * </ul>
 */
package com.proveyu.college;
