package com.proveyu.student.prep.web;

import com.proveyu.student.prep.application.PrepService;
import com.proveyu.student.prep.application.ProctorService;
import com.proveyu.student.prep.domain.PrepProctorEvent;
import com.proveyu.student.prep.domain.PrepModel.*;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PrepControllers {
    private PrepControllers() {}

    private static UUID uid(Authentication a) {
        return UUID.fromString(a.getName());
    }

    /** A proctoring screenshot: never cached, never sniffed as anything but an image. */
    private static ResponseEntity<byte[]> image(ProctorService.Screenshot s) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(s.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").header("X-Content-Type-Options", "nosniff").body(s.bytes());
    }

    /** Platform admin uploads and publishes mock tests. */
    @Tag(name = "Admin - Mock tests")
    @RestController
    @RequestMapping("/api/v1/admin/prep/tests")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiredArgsConstructor
    public static class AdminPrepController {
        private final PrepService service;
        private final org.springframework.context.ApplicationEventPublisher events;

        @GetMapping
        public ApiResponse<List<AdminTestSummary>> list() {
            return ApiResponse.success(service.adminList());
        }

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ApiResponse<AdminTestSummary> upload(Authentication auth, @RequestPart("file") MultipartFile file) throws IOException {
            if (file.isEmpty() || file.getSize() > 2_000_000) {
                throw new DomainException("Upload a JSON file up to 2 MB", HttpStatus.BAD_REQUEST, "INVALID_TEST");
            }
            AdminTestSummary t = service.upload(uid(auth), new String(file.getBytes(), StandardCharsets.UTF_8));
            events.publishEvent(new com.proveyu.shared.audit.AdminActionEvent(uid(auth), "MOCK_TEST_UPLOADED", "PREP_TEST", t.id().toString(), "Uploaded mock test \"" + t.title() + "\" (draft)"));
            return ApiResponse.success(t, "Test uploaded as a draft");
        }

        @PatchMapping("/{id}/publish")
        public ApiResponse<AdminTestSummary> publish(Authentication auth, @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
            boolean published = Boolean.TRUE.equals(body.get("published"));
            AdminTestSummary t = service.setPublished(id, published);
            events.publishEvent(new com.proveyu.shared.audit.AdminActionEvent(uid(auth), published ? "MOCK_TEST_PUBLISHED" : "MOCK_TEST_UNPUBLISHED", "PREP_TEST", id.toString(),
                    (published ? "Published" : "Unpublished") + " mock test \"" + t.title() + "\""));
            return ApiResponse.success(t, published ? "Test published to students" : "Test hidden from students");
        }

        @DeleteMapping("/{id}")
        public ApiResponse<Void> delete(Authentication auth, @PathVariable UUID id) {
            service.delete(id);
            events.publishEvent(new com.proveyu.shared.audit.AdminActionEvent(uid(auth), "MOCK_TEST_DELETED", "PREP_TEST", id.toString(), "Deleted mock test " + id));
            return ApiResponse.success(null, "Test deleted");
        }

        @GetMapping("/{id}/definition")
        public ResponseEntity<String> definition(@PathVariable UUID id) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mock-test-" + id + ".json\"").body(service.definition(id));
        }
    }

    /** Students: list mock tests, take a timed attempt, run code, submit, see results. */
    @Tag(name = "Student - Preparation")
    @RestController
    @RequestMapping("/api/v1/college/student/prep")
    @PreAuthorize("hasRole('CANDIDATE')")
    @RequiredArgsConstructor
    public static class StudentPrepController {
        private final PrepService service;
        private final ProctorService proctor;

        @GetMapping("/tests")
        public ApiResponse<List<TestCard>> tests(Authentication auth) {
            return ApiResponse.success(service.studentTests(uid(auth)));
        }

        @PostMapping("/tests/{id}/start")
        public ApiResponse<AttemptView> start(Authentication auth, @PathVariable UUID id) {
            return ApiResponse.success(service.start(uid(auth), id));
        }

        @GetMapping("/attempts")
        public ApiResponse<List<AttemptHistory>> history(Authentication auth) {
            return ApiResponse.success(service.history(uid(auth)));
        }

        @GetMapping("/attempts/{id}")
        public ApiResponse<AttemptView> attempt(Authentication auth, @PathVariable UUID id) {
            return ApiResponse.success(service.attempt(uid(auth), id));
        }

        @PutMapping("/attempts/{id}/answers")
        public ApiResponse<Void> save(Authentication auth, @PathVariable UUID id, @RequestBody Map<String, Answer> answers) {
            service.saveAnswers(uid(auth), id, answers);
            return ApiResponse.success(null, "Saved");
        }

        @PostMapping("/attempts/{id}/run")
        public ApiResponse<RunResponse> run(Authentication auth, @PathVariable UUID id, @RequestBody RunRequest body) {
            return ApiResponse.success(service.run(uid(auth), id, body));
        }

        @PostMapping("/attempts/{id}/submit")
        public ApiResponse<AttemptResult> submit(Authentication auth, @PathVariable UUID id, @RequestBody(required = false) Map<String, Answer> answers) {
            return ApiResponse.success(service.submit(uid(auth), id, answers), "Test submitted");
        }

        @GetMapping("/attempts/{id}/result")
        public ApiResponse<AttemptResult> result(Authentication auth, @PathVariable UUID id) {
            return ApiResponse.success(service.result(uid(auth), id));
        }

        /**
         * The student left the running test (kind TAB_HIDDEN, WINDOW_BLUR or SHARE_STOPPED), with a screenshot of the shared screen.
         * After the allowed warnings, the next switch submits the test (autoSubmitted = true).
         */
        @PostMapping(value = "/attempts/{id}/proctor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ApiResponse<ProctorStatus> reportSwitch(Authentication auth, @PathVariable UUID id, @RequestParam("kind") PrepProctorEvent.Kind kind,
                                                       @RequestParam(value = "clientAt", required = false) Instant clientAt,
                                                       @RequestPart(value = "screenshot", required = false) MultipartFile screenshot) throws IOException {
            byte[] image = screenshot == null || screenshot.isEmpty() ? null : screenshot.getBytes();
            ProctorStatus st = proctor.reportSwitch(uid(auth), id, kind, clientAt, image);
            return ApiResponse.success(st, st.autoSubmitted() ? "Test submitted after leaving it too many times" : "Switch recorded");
        }

        @GetMapping("/attempts/{id}/proctor")
        public ApiResponse<ProctorLog> proctorLog(Authentication auth, @PathVariable UUID id) {
            return ApiResponse.success(proctor.log(uid(auth), id));
        }

        @GetMapping("/proctor/{eventId}/screenshot")
        public ResponseEntity<byte[]> screenshot(Authentication auth, @PathVariable UUID eventId) {
            return image(proctor.screenshot(uid(auth), eventId));
        }
    }

    /** The placement cell sees its students' mock test attempts and, as proof, every time a student left a test. */
    @Tag(name = "College - Mock test attempts")
    @RestController
    @RequestMapping("/api/v1/college/prep")
    @PreAuthorize(com.proveyu.college.security.CollegeAccess.READ)
    @RequiredArgsConstructor
    public static class CollegeProctorController {
        private final ProctorService proctor;

        @GetMapping("/attempts")
        public ApiResponse<List<CollegeAttemptRow>> attempts(com.proveyu.college.security.CollegeContext ctx,
                                                             @RequestParam(defaultValue = "false") boolean flagged) {
            return ApiResponse.success(proctor.collegeAttempts(ctx, flagged));
        }

        @GetMapping("/attempts/{id}/proctor")
        public ApiResponse<ProctorLog> proctorLog(com.proveyu.college.security.CollegeContext ctx, @PathVariable UUID id) {
            return ApiResponse.success(proctor.collegeLog(ctx, id));
        }

        @GetMapping("/proctor/{eventId}/screenshot")
        public ResponseEntity<byte[]> screenshot(com.proveyu.college.security.CollegeContext ctx, @PathVariable UUID eventId) {
            return image(proctor.collegeScreenshot(ctx, eventId));
        }
    }

    /**
     * A college's placement team (TPO) uploads mock tests for its own students. They appear under the students' "More practice
     * tests" (never in the unlock path) and only for that college. Admin and coordinator manage; viewers can see the list.
     */
    @Tag(name = "College - Mock tests")
    @RestController
    @RequestMapping("/api/v1/college/prep/tests")
    @RequiredArgsConstructor
    public static class CollegePrepController {
        private final PrepService service;
        private final com.proveyu.college.application.AuditService audit;

        @GetMapping
        @PreAuthorize(com.proveyu.college.security.CollegeAccess.READ)
        public ApiResponse<List<AdminTestSummary>> list(com.proveyu.college.security.CollegeContext ctx) {
            return ApiResponse.success(service.collegeList(ctx.collegeId()));
        }

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize(com.proveyu.college.security.CollegeAccess.WRITE)
        public ApiResponse<AdminTestSummary> upload(com.proveyu.college.security.CollegeContext ctx, @RequestPart("file") MultipartFile file) throws IOException {
            if (file.isEmpty() || file.getSize() > 2_000_000) {
                throw new DomainException("Upload a JSON file up to 2 MB", HttpStatus.BAD_REQUEST, "INVALID_TEST");
            }
            AdminTestSummary t = service.collegeUpload(ctx.collegeId(), ctx.userId(), new String(file.getBytes(), StandardCharsets.UTF_8));
            audit.log(ctx, "MOCK_TEST_UPLOADED", "PREP_TEST", t.id(), "Uploaded mock test \"" + t.title() + "\" (draft)", null);
            return ApiResponse.success(t, "Test uploaded as a draft");
        }

        @PatchMapping("/{id}/publish")
        @PreAuthorize(com.proveyu.college.security.CollegeAccess.WRITE)
        public ApiResponse<AdminTestSummary> publish(com.proveyu.college.security.CollegeContext ctx, @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
            boolean on = Boolean.TRUE.equals(body.get("published"));
            AdminTestSummary t = service.collegeSetPublished(ctx.collegeId(), id, on);
            audit.log(ctx, on ? "MOCK_TEST_PUBLISHED" : "MOCK_TEST_UNPUBLISHED", "PREP_TEST", id,
                    (on ? "Published" : "Unpublished") + " mock test \"" + t.title() + "\"", null);
            return ApiResponse.success(t, on ? "Published to your students" : "Hidden from your students");
        }

        @DeleteMapping("/{id}")
        @PreAuthorize(com.proveyu.college.security.CollegeAccess.WRITE)
        public ApiResponse<Void> delete(com.proveyu.college.security.CollegeContext ctx, @PathVariable UUID id) {
            var t = service.collegeDelete(ctx.collegeId(), id);
            audit.log(ctx, "MOCK_TEST_DELETED", "PREP_TEST", id, "Deleted mock test \"" + t.getTitle() + "\" and its attempts", null);
            return ApiResponse.success(null, "Test deleted");
        }

        /** The uploaded JSON, to edit and upload again. */
        @GetMapping("/{id}/definition")
        @PreAuthorize(com.proveyu.college.security.CollegeAccess.READ)
        public ResponseEntity<String> definition(com.proveyu.college.security.CollegeContext ctx, @PathVariable UUID id) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mock-test-" + id + ".json\"")
                    .body(service.collegeTest(ctx.collegeId(), id).getDefinitionJson());
        }
    }
}
