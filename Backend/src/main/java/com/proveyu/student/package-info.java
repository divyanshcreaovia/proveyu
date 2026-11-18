/**
 * Student module: everything a college student (role {@code CANDIDATE}) uses.
 * <ul>
 *   <li>{@code application}, {@code web}: the student portal at {@code /api/v1/college/student/**} (drives, applications,
 *       interviews, offers, announcements, documents, profile) and account activation ({@code /api/v1/college/auth/student-signup}).</li>
 *   <li>{@code interview}: the AI mock interview, including its admin payment pages.</li>
 *   <li>{@code prep}: the mock test path, including the college and admin test uploads.</li>
 * </ul>
 * The student portal works on the college module's data (students, drives, applications, offers): it calls the college
 * services so eligibility, placement policy and notifications behave exactly as for the placement cell.
 */
package com.proveyu.student;
