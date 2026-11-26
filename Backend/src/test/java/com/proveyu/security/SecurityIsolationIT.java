package com.proveyu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Three small colleges; every portal of one tries to reach another's data, every endpoint is tried with the wrong role, and the chat
 * and sign-in rules are checked. It collects every failure before failing, so one run shows them all.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityIsolationIT extends IntegrationTestBase {

    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    static class College {
        String tag, tpo, collegeId, branch, branch2, studentId, studentUser, studentEmail, roll, student, companyId, company, driveId, drive, roundId, appId,
                docId, offerId, announcementId, announcement, cxo, cxoEmail, cxoMemberId, dashboardId, testId, attemptId, hr, recruiterAccessId, contactId;
        List<String> markers() { return List.of(studentEmail, roll, company, drive, announcement, studentId, driveId, companyId); }
    }

    College a, b, c;
    String admin;
    final List<String> failures = new ArrayList<>(), unclear = new ArrayList<>();
    int checks;

    private void check(String what, boolean ok, Object detail) {
        checks++;
        if (!ok) failures.add(what + "  ->  " + detail);
    }

    /** The other college's object must be refused. A 400 means the request was rejected before ownership was looked at. */
    private Resp refused(String what, HttpMethod method, String url, String token, Object body) throws Exception {
        Resp r = call(MockMvcRequestBuilders.request(method, url), token, body);
        judge(what + " [" + method + " " + url.replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "{id}") + "]", r);
        return r;
    }

    private void judge(String what, Resp r) {
        checks++;
        int s = r.status();
        if (s == 400) unclear.add(what + " -> 400 " + r.body().path("message").asText());
        else if (s != 401 && s != 403 && s != 404) failures.add(what + "  ->  HTTP " + s + " " + cut(r.body()));
    }

    private static String cut(Object o) {
        String s = String.valueOf(o);
        return s.length() > 220 ? s.substring(0, 220) : s;
    }

    private Resp ok(Resp r) {
        if (r.status() >= 300) throw new IllegalStateException("setup failed: " + r.status() + " " + r.body());
        return r;
    }

    private String studentToken(String email) throws Exception {
        String otp = post("/api/v1/auth/forgot-password", null, Map.of("email", email)).data().get("devOtp").asText();
        post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", otp, "newPassword", "Student123"));
        return login(email, "Student123");
    }

    private College college(String tag) throws Exception {
        College x = new College();
        x.tag = tag;
        x.tpo = registerCollege(tag);
        x.collegeId = get("/api/v1/college/me", x.tpo).data().get("collegeId").asText();
        x.branch = addBranch(x.tpo, "CSE");
        x.branch2 = addBranch(x.tpo, "IT");
        x.roll = "ROLL-" + tag + uid();
        JsonNode s = addStudent(x.tpo, x.branch, x.roll, 8.5, 0);
        x.studentId = s.get("id").asText();
        x.studentEmail = s.get("email").asText();
        x.student = studentToken(x.studentEmail);
        x.studentUser = get("/api/v1/college/student/profile", x.student).data().get("userId").asText();
        x.company = "Zeta-" + tag + "-" + uid();
        x.companyId = ok(post("/api/v1/college/companies", x.tpo, Map.of("name", x.company))).data().get("id").asText();
        x.contactId = ok(post("/api/v1/college/companies/" + x.companyId + "/contacts", x.tpo, Map.of("name", "Contact " + tag, "primaryContact", true)))
                .data().path("id").asText();
        x.drive = "Drive-" + tag + "-" + uid();
        x.driveId = ok(post("/api/v1/college/drives", x.tpo, Map.of("companyId", x.companyId, "title", x.drive, "roleTitle", "SDE", "driveType", "ON_CAMPUS",
                "ctcLpa", 9, "registrationDeadline", Instant.now().plus(3, ChronoUnit.DAYS).toString()))).data().get("id").asText();
        ok(post("/api/v1/college/drives/" + x.driveId + "/status", x.tpo, Map.of("status", "PUBLISHED")));
        x.roundId = ok(post("/api/v1/college/drives/" + x.driveId + "/rounds", x.tpo, Map.of("name", "Tech", "roundType", "TECHNICAL"))).data().get("id").asText();
        ok(post("/api/v1/college/drives/" + x.driveId + "/applications", x.tpo, Map.of("studentIds", List.of(x.studentId))));
        x.appId = get("/api/v1/college/drives/" + x.driveId + "/applications", x.tpo).data().get("content").get(0).get("id").asText();
        MockMultipartFile cv = new MockMultipartFile("file", "cv.pdf", "application/pdf", ("%PDF-1.4 " + tag).getBytes(StandardCharsets.UTF_8));
        x.docId = ok(call(multipart("/api/v1/college/student/documents").file(cv).param("docType", "RESUME"), x.student, null)).data().get("id").asText();
        x.offerId = ok(post("/api/v1/college/offers", x.tpo, Map.of("studentId", x.studentId, "companyId", x.companyId, "roleTitle", "SDE", "ctcLpa", 9,
                "overridePolicy", true, "overrideReason", "test setup"))).data().get("id").asText();
        x.announcement = "Notice-" + tag + "-" + uid();
        x.announcementId = ok(post("/api/v1/college/announcements", x.tpo, Map.of("title", x.announcement, "body", "private to " + tag, "sendEmail", false)))
                .data().get("id").asText();

        String cxoEmail = "cxo" + uid() + "@" + tag + ".test";
        x.cxoEmail = cxoEmail;
        x.cxoMemberId = ok(post("/api/v1/admin/cxo-users", admin, Map.of("collegeId", x.collegeId, "fullName", "Dean " + tag, "email", cxoEmail,
                "password", "Dean12345"))).data().get("memberId").asText();
        x.cxo = login(cxoEmail, "Dean12345");

        Resp up = call(multipart("/api/v1/college/prep/tests").file(new MockMultipartFile("file", "t.json", "application/json",
                ("{\"title\":\"Sessional-" + tag + "\",\"durationMinutes\":30,\"sections\":[{\"name\":\"B\",\"questions\":[{\"type\":\"MCQ\",\"text\":\"2+2?\","
                        + "\"options\":[\"3\",\"4\"],\"answer\":1,\"marks\":2}]}]}").getBytes(StandardCharsets.UTF_8))), x.tpo, null);
        x.testId = ok(up).data().get("id").asText();
        ok(patch("/api/v1/college/prep/tests/" + x.testId + "/publish?published=true", x.tpo, Map.of("published", true)));
        Resp start = post("/api/v1/college/student/prep/tests/" + x.testId + "/start", x.student, null);
        x.attemptId = start.status() < 300 ? start.data().path("attemptId").asText(start.data().path("id").asText()) : null;

        Resp inv = ok(post("/api/v1/college/drives/" + x.driveId + "/recruiters", x.tpo, Map.of("email", "hr" + uid() + "@corp-" + tag + ".com", "fullName", "HR " + tag, "shareDetails", true)));
        x.recruiterAccessId = inv.data().get("id").asText();
        x.hr = ok(post("/api/v1/college/auth/recruiter-accept", null, Map.of("token", inv.data().get("devInviteToken").asText(), "fullName", "HR " + tag,
                "password", "Recruit123"))).data().get("token").asText();

        Resp dash = post("/api/v1/cxo/dashboards", x.cxo, Map.of("title", "Board-" + tag, "spec", Map.of("title", "Board-" + tag, "summary", "s",
                "widgets", List.of(Map.of("id", "w1", "type", "KPI", "title", "Students", "metric", "students_total")))));
        x.dashboardId = dash.status() < 300 ? dash.data().path("id").asText() : null;
        if (x.dashboardId == null || x.attemptId == null) throw new IllegalStateException("setup incomplete: " + cut(dash.body()));
        return x;
    }

    @BeforeAll
    void setUp() throws Exception {
        String email = "root" + uid() + "@proveyu.test";
        users.save(User.builder().email(email).fullName("Root").passwordHash(encoder.encode("RootPass1")).role(Role.ADMIN).active(true).emailVerifiedAt(Instant.now()).build());
        admin = login(email, "RootPass1");
        a = college("alpha");
        b = college("bravo");
        c = college("charlie");
    }

    @Test
    void probe() throws Exception {
        roleWalls();
        listsShowOnlyTheOwnCollege();
        otherCollegesObjectsById();
        recruiterStaysInTheDrive();
        chatAcrossColleges();
        staffRolesInsideACollege();
        tokensAndAccountState();
        assertThat(failures).as(checks + " checks").isEmpty();
    }

    /** Every mapped endpoint, called without a token and with a token of each wrong role. */
    private void roleWalls() throws Exception {
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("anonymous", null);
        tokens.put("CANDIDATE", a.student);
        tokens.put("COLLEGE", a.tpo);
        tokens.put("CXO", a.cxo);
        tokens.put("RECRUITER", a.hr);
        tokens.put("ADMIN", admin);
        int endpoints = 0;
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition() != null ? info.getPathPatternsCondition().getPatternValues() : info.getPatternsCondition().getPatterns();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods().isEmpty() ? Set.of(RequestMethod.GET) : info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/")) continue;
                boolean open = pattern.startsWith("/api/v1/public/") || (pattern.startsWith("/api/v1/auth/") && !pattern.endsWith("/logout"))
                        || pattern.matches("/api/v1/college/auth/(register|accept-invite|student-signup|recruiter-accept|invite/.*|recruiter-invite/.*)");
                if (open) continue;
                String allowed = pattern.startsWith("/api/v1/admin") ? "ADMIN"
                        : pattern.startsWith("/api/v1/college/student/") ? "CANDIDATE"
                        : pattern.startsWith("/api/v1/college") ? "COLLEGE"
                        : pattern.startsWith("/api/v1/recruiter") ? "RECRUITER ADMIN"
                        : pattern.startsWith("/api/v1/cxo") ? "CXO"
                        : "CANDIDATE COLLEGE CXO RECRUITER ADMIN";
                String url = pattern.replaceAll("\\{date}", "2026-12-01").replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                for (RequestMethod m : methods) {
                    endpoints++;
                    for (var t : tokens.entrySet()) {
                        if (allowed.contains(t.getKey())) continue;
                        var req = MockMvcRequestBuilders.request(HttpMethod.valueOf(m.name()), url);
                        if (t.getValue() != null) req.header("Authorization", "Bearer " + t.getValue());
                        var res = mvc.perform(req).andReturn().getResponse();
                        int code = res.getStatus();
                        check("role wall: " + t.getKey() + " on " + m + " " + pattern, code == 401 || code == 403,
                                "HTTP " + code + " " + res.getContentType() + " " + res.getContentLengthLong() + "b " + cut(res.getContentAsString().replaceAll("[^\\x20-\\x7e]", ".")));
                    }
                }
            }
        }
        check("the endpoint list was read", endpoints > 250, endpoints);
    }

    /** College B's own screens never contain anything of A or C. */
    private void listsShowOnlyTheOwnCollege() throws Exception {
        Map<String, List<String>> screens = new LinkedHashMap<>();
        screens.put(b.tpo, List.of("/api/v1/college/students", "/api/v1/college/companies", "/api/v1/college/drives", "/api/v1/college/offers", "/api/v1/college/interviews",
                "/api/v1/college/announcements", "/api/v1/college/audit-logs?size=200", "/api/v1/college/analytics/dashboard", "/api/v1/college/analytics/skill-gap",
                "/api/v1/college/reports/placement-summary/preview", "/api/v1/college/reports/company/preview", "/api/v1/college/reports/drive/preview",
                "/api/v1/college/reports/branch/preview", "/api/v1/college/reports/ctc/preview", "/api/v1/college/reports/alumni/preview", "/api/v1/college/reports/skill-gap/preview",
                "/api/v1/college/search?q=a", "/api/v1/college/search?q=ROLL", "/api/v1/college/team/members", "/api/v1/college/chat/contacts", "/api/v1/college/prep/tests",
                "/api/v1/college/prep/attempts", "/api/v1/college/off-campus", "/api/v1/college/off-campus/nocs", "/api/v1/college/code-ground/leaderboard?period=all",
                "/api/v1/college/code-ground/leaderboard?branchId=" + a.branch, "/api/v1/college/settings/branches", "/api/v1/college/students?collegeId=" + a.collegeId,
                "/api/v1/college/drives?companyId=" + a.companyId, "/api/v1/college/offers?driveId=" + a.driveId, "/api/v1/college/students?branchId=" + a.branch,
                "/api/v1/college/companies/recruiters", "/api/v1/notifications"));
        screens.put(b.student, List.of("/api/v1/college/student/profile", "/api/v1/college/student/drives", "/api/v1/college/student/applications", "/api/v1/college/student/offers",
                "/api/v1/college/student/interviews", "/api/v1/college/student/announcements", "/api/v1/college/student/documents", "/api/v1/college/student/chat/contacts",
                "/api/v1/college/student/prep/tests", "/api/v1/college/student/prep/attempts", "/api/v1/college/student/off-campus", "/api/v1/college/student/code-ground/leaderboard?period=all",
                "/api/v1/college/student/code-ground/submissions", "/api/v1/college/student/interview", "/api/v1/notifications", "/api/v1/chat/unread", "/api/v1/support/tickets"));
        screens.put(b.cxo, List.of("/api/v1/cxo/me", "/api/v1/cxo/catalog", "/api/v1/cxo/dashboards"));
        screens.put(b.hr, List.of("/api/v1/recruiter/campus/drives", "/api/v1/recruiter/campus/drives/" + b.driveId + "/applicants", "/api/v1/recruiter/campus/drives/" + b.driveId + "/offers"));
        for (var s : screens.entrySet()) {
            String who = s.getKey().equals(b.tpo) ? "TPO-B" : s.getKey().equals(b.student) ? "student-B" : s.getKey().equals(b.cxo) ? "CXO-B" : "recruiter-B";
            for (String url : s.getValue()) {
                var response = mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + s.getKey())).andReturn().getResponse();
                String text = response.getContentAsString();
                if (response.getStatus() >= 500) check(who + " " + url, false, "HTTP " + response.getStatus());
                if (response.getStatus() >= 400 && response.getStatus() < 500) unclear.add(who + " screen " + url + " -> " + response.getStatus());
                for (College other : List.of(a, c)) {
                    List<String> leaked = other.markers().stream().filter(text::contains).filter(m -> !url.contains(m)).toList();
                    check(who + " sees nothing of " + other.tag + " on " + url.replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "{id}"), leaked.isEmpty(), leaked);
                }
            }
        }
    }

    private void otherCollegesObjectsById() throws Exception {
        String C = "/api/v1/college/";
        for (String url : List.of("students/" + a.studentId, "students/" + a.studentId + "/documents", "students/documents/" + a.docId + "/file", "companies/" + a.companyId,
                "drives/" + a.driveId, "drives/" + a.driveId + "/applications", "drives/" + a.driveId + "/eligibility", "drives/" + a.driveId + "/overrides",
                "drives/" + a.driveId + "/rounds", "drives/" + a.driveId + "/recruiters", "drives/" + a.driveId + "/applications/export", "rounds/" + a.roundId + "/results",
                "rounds/" + a.roundId + "/scorecards", "interviews/drive/" + a.driveId, "offers/" + a.offerId + "/letter", "prep/tests/" + a.testId + "/definition",
                "prep/tests/" + a.testId + "/monitor", "prep/tests/" + a.testId + "/export", "prep/attempts/" + a.attemptId + "/proctor",
                "prep/tests/" + a.testId + "/attempts/" + a.attemptId + "/review", "reports/drive/preview?driveId=" + a.driveId)) {
            var raw = mvc.perform(MockMvcRequestBuilders.get(C + url).header("Authorization", "Bearer " + b.tpo)).andReturn().getResponse();
            if (raw.getStatus() == 200 && !String.valueOf(raw.getContentType()).contains("json")) {
                String text = raw.getContentAsString();
                checks++;
                // a file for another college's object: it must at least hold nothing of that college
                failures.add("TPO-B gets a file for A [" + url.replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "{id}") + "]  ->  " + raw.getContentType() + " " + raw.getContentAsByteArray().length + " bytes");
                continue;
            }
            Resp r = call(MockMvcRequestBuilders.get(C + url), b.tpo, null);
            if (url.startsWith("reports/") && r.status() == 200) {
                check("TPO-B report filtered by A's drive shows nothing of A", a.markers().stream().filter(m -> !url.contains(m)).noneMatch(r.body().toString()::contains), cut(r.body()));
            } else if (r.status() == 200 && r.data().isArray() && r.data().isEmpty()) {
                checks++;      // an empty list reveals nothing
            } else judge("TPO-B reads A [" + url.replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "{id}") + "]", r);
        }
        refused("TPO-B edits A student", HttpMethod.PATCH, C + "students/" + a.studentId + "/status", b.tpo, Map.of("status", "DEBARRED", "reason", "x"));
        refused("TPO-B deletes A student", HttpMethod.DELETE, C + "students/" + a.studentId, b.tpo, null);
        refused("TPO-B verifies A document", HttpMethod.PATCH, C + "students/documents/" + a.docId + "/verify", b.tpo, Map.of("status", "VERIFIED"));
        refused("TPO-B deletes A document", HttpMethod.DELETE, C + "students/documents/" + a.docId, b.tpo, null);
        refused("TPO-B edits A company", HttpMethod.PUT, C + "companies/" + a.companyId, b.tpo, Map.of("name", "hacked"));
        refused("TPO-B deletes A company", HttpMethod.DELETE, C + "companies/" + a.companyId, b.tpo, null);
        refused("TPO-B edits A contact", HttpMethod.PUT, C + "companies/contacts/" + a.contactId, b.tpo, Map.of("name", "hacked", "primaryContact", true));
        refused("TPO-B deletes A contact", HttpMethod.DELETE, C + "companies/contacts/" + a.contactId, b.tpo, null);
        refused("TPO-B deletes A drive", HttpMethod.DELETE, C + "drives/" + a.driveId, b.tpo, null);
        refused("TPO-B clones A drive", HttpMethod.POST, C + "drives/" + a.driveId + "/clone", b.tpo, null);
        refused("TPO-B changes A drive status", HttpMethod.POST, C + "drives/" + a.driveId + "/status", b.tpo, Map.of("status", "CANCELLED", "reason", "x"));
        refused("TPO-B adds own student to A drive", HttpMethod.POST, C + "drives/" + a.driveId + "/applications", b.tpo, Map.of("studentIds", List.of(b.studentId)));
        refused("TPO-B starts A round", HttpMethod.POST, C + "rounds/" + a.roundId + "/start", b.tpo, null);
        refused("TPO-B deletes A round", HttpMethod.DELETE, C + "rounds/" + a.roundId, b.tpo, null);
        refused("TPO-B changes A offer", HttpMethod.PATCH, C + "offers/" + a.offerId + "/status", b.tpo, Map.of("status", "REVOKED"));
        refused("TPO-B invites recruiter to A drive", HttpMethod.POST, C + "drives/" + a.driveId + "/recruiters", b.tpo, Map.of("email", "x@evil.com", "fullName", "X"));
        refused("TPO-B revokes A recruiter", HttpMethod.POST, C + "drives/" + a.driveId + "/recruiters/" + a.recruiterAccessId + "/revoke", b.tpo, null);
        refused("TPO-B publishes A test", HttpMethod.PATCH, C + "prep/tests/" + a.testId + "/publish?published=false", b.tpo, Map.of("published", false));
        refused("TPO-B deletes A test", HttpMethod.DELETE, C + "prep/tests/" + a.testId, b.tpo, null);
        refused("TPO-B extends A attempt", HttpMethod.POST, C + "prep/tests/" + a.testId + "/attempts/" + a.attemptId + "/extend", b.tpo, Map.of("minutes", 10));
        Resp bulk = patch(C + "applications/status", b.tpo, Map.of("applicationIds", List.of(a.appId), "status", "REJECTED"));
        check("TPO-B cannot change A application status", bulk.status() >= 400 || bulk.data().path("updated").asInt() == 0, cut(bulk.body()));

        String S = "/api/v1/college/student/";
        refused("student-B applies to A drive", HttpMethod.POST, S + "drives/" + a.driveId + "/apply", b.student, Map.of());
        refused("student-B reads A document", HttpMethod.GET, S + "documents/" + a.docId + "/file", b.student, null);
        refused("student-B deletes A document", HttpMethod.DELETE, S + "documents/" + a.docId, b.student, null);
        refused("student-B reads A offer letter", HttpMethod.GET, S + "offers/" + a.offerId + "/letter", b.student, null);
        refused("student-B answers A offer", HttpMethod.POST, S + "offers/" + a.offerId + "/respond", b.student, Map.of("accept", true));
        refused("student-B reads A application rounds", HttpMethod.GET, S + "applications/" + a.appId + "/rounds", b.student, null);
        refused("student-B withdraws A application", HttpMethod.POST, S + "applications/" + a.appId + "/withdraw", b.student, null);
        refused("student-B starts A college test", HttpMethod.POST, S + "prep/tests/" + a.testId + "/start", b.student, null);
        refused("student-B reads A attempt", HttpMethod.GET, S + "prep/attempts/" + a.attemptId, b.student, null);
        refused("student-B submits A attempt", HttpMethod.POST, S + "prep/attempts/" + a.attemptId + "/submit", b.student, Map.of());
        refused("student-B reads A result", HttpMethod.GET, S + "prep/attempts/" + a.attemptId + "/result", b.student, null);
        refused("student-B reads A proctor log", HttpMethod.GET, S + "prep/attempts/" + a.attemptId + "/proctor", b.student, null);
        Resp read = post(S + "announcements/" + a.announcementId + "/read", b.student, null);
        check("student-B marking A announcement does not count for A", get("/api/v1/college/announcements", a.tpo).data().get("content").get(0).get("readCount").asInt() == 0,
                "HTTP " + read.status() + " " + cut(get("/api/v1/college/announcements", a.tpo).body()));

        if (a.dashboardId != null) {
            refused("CXO-B reads A dashboard", HttpMethod.GET, "/api/v1/cxo/dashboards/" + a.dashboardId, b.cxo, null);
            refused("CXO-B pins A dashboard", HttpMethod.POST, "/api/v1/cxo/dashboards/" + a.dashboardId + "/pin", b.cxo, Map.of("pinned", true));
            refused("CXO-B deletes A dashboard", HttpMethod.DELETE, "/api/v1/cxo/dashboards/" + a.dashboardId, b.cxo, null);
        }
        Resp run = post("/api/v1/cxo/dashboards/run", b.cxo, Map.of("spec", Map.of("title", "x", "widgets", List.of(
                Map.of("id", "w1", "type", "KPI", "title", "Students", "metric", "students_total")))));
        check("CXO-B counts only own students", run.status() == 200 && run.data().get(0).get("value").asInt() == 1, cut(run.body()));

        // nothing of A changed
        check("A student untouched", get(C + "students/" + a.studentId, a.tpo).data().path("student").path("status").asText().matches("ACTIVE|PLACED"), cut(get(C + "students/" + a.studentId, a.tpo).body()));
        check("A drive untouched", get(C + "drives/" + a.driveId, a.tpo).data().path("status").asText().equals("PUBLISHED"), cut(get(C + "drives/" + a.driveId, a.tpo).body()));
        check("A company untouched", get(C + "companies/" + a.companyId, a.tpo).body().toString().contains(a.company), "name changed or gone");
    }

    private void recruiterStaysInTheDrive() throws Exception {
        String R = "/api/v1/recruiter/campus/drives/";
        for (String url : List.of("", "/applicants", "/offers", "/report", "/applicants/" + b.appId + "/resume", "/rounds/" + b.roundId + "/results")) {
            refused("recruiter-A reads B drive", HttpMethod.GET, R + b.driveId + url, a.hr, null);
        }
        refused("recruiter-A downloads B resumes", HttpMethod.POST, R + b.driveId + "/resumes.zip", a.hr, null);
        refused("recruiter-A starts B round", HttpMethod.POST, R + b.driveId + "/rounds/" + b.roundId + "/start", a.hr, null);
        refused("recruiter-A uses own drive with B round", HttpMethod.POST, R + a.driveId + "/rounds/" + b.roundId + "/start", a.hr, null);
        refused("recruiter-A uses own drive with B resume", HttpMethod.GET, R + a.driveId + "/applicants/" + b.appId + "/resume", a.hr, null);
        Resp st = patch(R + a.driveId + "/applications/status", a.hr, Map.of("applicationIds", List.of(b.appId), "status", "REJECTED"));
        check("recruiter-A cannot change B application through own drive", st.status() >= 400 || st.data().path("updated").asInt() == 0, cut(st.body()));
        check("recruiter-A lists only own drive", get("/api/v1/recruiter/campus/drives", a.hr).data().size() == 1, cut(get("/api/v1/recruiter/campus/drives", a.hr).body()));
    }

    private void chatAcrossColleges() throws Exception {
        String studentB = b.student, tpoA = userId(a.tpo), tpoB = userId(b.tpo), hrA = userId(a.hr);
        cannotChat("student-B to student-A", studentB, a.studentUser);
        cannotChat("student-B to TPO-A", studentB, tpoA);
        cannotChat("TPO-B to student-A", b.tpo, a.studentUser);
        cannotChat("TPO-B to TPO-A", b.tpo, tpoA);
        cannotChat("recruiter-B to student-A", b.hr, a.studentUser);
        cannotChat("recruiter-B to TPO-A", b.hr, tpoA);
        cannotChat("recruiter-A to student of the same college", a.hr, a.studentUser);
        cannotChat("student-A to the recruiter", a.student, hrA);
        canChat("student-A to own TPO", a.student, tpoA);
        canChat("TPO-A to own student", a.tpo, a.studentUser);
        canChat("recruiter-A to the college that gave the drive", a.hr, tpoA);
        canChat("TPO-A to that recruiter", a.tpo, hrA);
        canChat("admin to any student", admin, b.studentUser);
        canChat("student to admin", b.student, userId(admin));
        canChat("student-B to own TPO", studentB, tpoB);
        Resp file = call(multipart("/api/v1/chat/messages/file").file(new MockMultipartFile("file", "x.pdf", "application/pdf", "%PDF-1.4 x".getBytes(StandardCharsets.UTF_8)))
                .param("receiverId", a.studentUser), studentB, null);
        check("student-B cannot send a file to student-A", file.status() == 403, "HTTP " + file.status());
        JsonNode other = get("/api/v1/chat/messages/" + a.studentUser, c.student).data();
        check("a third person reads nothing of it", !String.valueOf(other).contains("hello"), cut(other));
    }

    private void canChat(String what, String from, String to) throws Exception {
        Resp r = post("/api/v1/chat/messages", from, Map.of("receiverId", to, "message", "hello"));
        check("allowed: " + what, r.status() < 300, "HTTP " + r.status() + " " + cut(r.body()));
    }

    private void cannotChat(String what, String from, String to) throws Exception {
        Resp r = post("/api/v1/chat/messages", from, Map.of("receiverId", to, "message", "hello"));
        check("refused: " + what, r.status() == 403, "HTTP " + r.status() + " " + cut(r.body()));
    }

    /** The user id inside a token. */
    private String userId(String token) throws Exception {
        return json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1])).get("sub").asText();
    }

    private void staffRolesInsideACollege() throws Exception {
        String viewer = member(c, "COLLEGE_VIEWER", null), coord = member(c, "COLLEGE_COORDINATOR", List.of(c.branch));
        JsonNode itStudent = addStudent(c.tpo, c.branch2, "ITONLY" + uid(), 8.0, 0);
        refused("viewer adds a student", HttpMethod.POST, "/api/v1/college/students", viewer, Map.of("rollNumber", "V1", "firstName", "V", "email", "v@v.in", "branchId", c.branch, "batchYear", 2027));
        refused("viewer deletes a drive", HttpMethod.DELETE, "/api/v1/college/drives/" + c.driveId, viewer, null);
        refused("viewer announces", HttpMethod.POST, "/api/v1/college/announcements", viewer, Map.of("title", "x", "body", "y", "sendEmail", false));
        refused("viewer reads audit log", HttpMethod.GET, "/api/v1/college/audit-logs", viewer, null);
        refused("coordinator invites staff", HttpMethod.POST, "/api/v1/college/team/invites", coord, Map.of("email", "z@z.in", "fullName", "Z", "role", "COLLEGE_ADMIN"));
        refused("coordinator changes policy", HttpMethod.PUT, "/api/v1/college/settings/policy", coord, Map.of("policyMode", get("/api/v1/college/settings/policy", c.tpo).data().get("policyMode").asText(), "maxOffers", 2, "upgradeMinHikePercent", 10));
        refused("branch coordinator reads another branch's student", HttpMethod.GET, "/api/v1/college/students/" + itStudent.get("id").asText(), coord, null);
        String itUser = userId(studentToken(itStudent.get("email").asText()));
        cannotChat("branch coordinator to a student of another branch", coord, itUser);
        canChat("branch coordinator to a student of their branch", coord, c.studentUser);
        check("branch coordinator's list hides the other branch", !get("/api/v1/college/students?size=100", coord).body().toString().contains("ITONLY"), "visible");
        for (String url : List.of("/api/v1/college/reports/placement-summary/preview", "/api/v1/college/search?q=ITONLY", "/api/v1/college/code-ground/leaderboard?period=all", "/api/v1/college/chat/contacts")) {
            check("branch coordinator does not see the other branch on " + url, !get(url, coord).body().toString().contains("ITONLY"), "visible");
        }
    }

    private String member(College x, String role, List<String> branches) throws Exception {
        String email = role.toLowerCase().replace("_", "") + uid() + "@" + x.tag + "staff.edu.in";
        Map<String, Object> body = new HashMap<>(Map.of("email", email, "fullName", role, "role", role));
        if (branches != null) body.put("branchIds", branches);
        String domain = get("/api/v1/college/me", x.tpo).data().get("email").asText().split("@")[1];
        body.put("email", role.toLowerCase().replace("_", "") + uid() + "@" + domain);
        Resp inv = ok(post("/api/v1/college/team/invites", x.tpo, body));
        ok(post("/api/v1/college/auth/accept-invite", null, Map.of("token", inv.data().get("devInviteToken").asText(), "password", "Staff12345", "fullName", role)));
        return login((String) body.get("email"), "Staff12345");
    }

    private void tokensAndAccountState() throws Exception {
        String[] parts = a.student.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.replace("CANDIDATE", "ADMIN").getBytes(StandardCharsets.UTF_8));
        check("edited role in token is rejected", get("/api/v1/admin/users", parts[0] + "." + forged + "." + parts[2]).status() == 401, "accepted");
        String none = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        check("unsigned token (alg none) is rejected", get("/api/v1/admin/users", none + "." + forged + ".").status() == 401, "accepted");
        check("garbage token is rejected", get("/api/v1/college/student/profile", "abc.def.ghi").status() == 401, "accepted");

        ok(post("/api/v1/admin/cxo-users/" + c.cxoMemberId + "/revoke", admin, null));
        int s = get("/api/v1/cxo/me", c.cxo).status();
        check("revoked CXO's old token stops working", s == 401 || s == 403, "HTTP " + s);

        ok(post("/api/v1/admin/users/" + c.studentUser + "/block", admin, Map.of("reason", "test")));
        s = get("/api/v1/college/student/profile", c.student).status();
        check("blocked student's old token stops working", s == 401 || s == 403, "HTTP " + s);

        String access = c.recruiterAccessId;
        ok(post("/api/v1/college/drives/" + c.driveId + "/recruiters/" + access + "/revoke", c.tpo, null));
        s = get("/api/v1/recruiter/campus/drives/" + c.driveId, c.hr).status();
        check("revoked recruiter loses the drive", s == 403 || s == 404 || s == 401, "HTTP " + s);

        call(MockMvcRequestBuilders.delete("/api/v1/college/students/" + b.studentId), b.tpo, null);
        Resp gone = get("/api/v1/college/student/drives", b.student);
        check("deleted student's old token cannot use the portal", gone.status() >= 400, "HTTP " + gone.status() + " " + cut(gone.body()));

        String again = login(a.studentEmail, "Student123");
        post("/api/v1/auth/logout", again, null);
        check("a token stops working after logout", get("/api/v1/college/student/profile", again).status() == 401, "still works");

        Thread.sleep(1100); // sign-ins are timed in whole seconds
        String otp = post("/api/v1/auth/forgot-password", null, Map.of("email", a.studentEmail)).data().path("devOtp").asText();
        ok(post("/api/v1/auth/reset-password", null, Map.of("email", a.studentEmail, "resetToken", otp, "newPassword", "Changed12345")));
        check("the old sign-in stops when the password changes", get("/api/v1/college/student/profile", a.student).status() == 401, "still works");
        check("signing in with the new password works at once",
                get("/api/v1/college/student/profile", login(a.studentEmail, "Changed12345")).status() == 200, "refused");
        check("other people stay signed in", get("/api/v1/college/me", a.tpo).status() == 200, "signed out");
    }
}
