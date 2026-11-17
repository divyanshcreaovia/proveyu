package com.proveyu.interview.web;

import com.proveyu.interview.application.AiInterviewService;
import com.proveyu.interview.application.InterviewPurchaseService;
import com.proveyu.interview.domain.InterviewModel.*;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public final class InterviewControllers {
    private InterviewControllers() {}

    private static UUID uid(Authentication a) {
        return UUID.fromString(a.getName());
    }

    private static ResponseEntity<byte[]> wav(byte[] body) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav")).cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(body);
    }

    /** Students: AI mock interview (Preparation > AI interview). */
    @Tag(name = "Student - AI interview")
    @RestController
    @RequestMapping("/api/v1/college/student/interview")
    @PreAuthorize("hasRole('CANDIDATE')")
    @RequiredArgsConstructor
    public static class StudentInterviewController {
        private final AiInterviewService service;
        private final InterviewPurchaseService purchases;

        /** Buy AI interviews: a UPI payment request (QR link) at the server's price. */
        @PostMapping("/purchases")
        public ApiResponse<PurchaseOrder> buy(Authentication auth, @RequestBody PurchaseRequest body) {
            return ApiResponse.success(purchases.createOrder(uid(auth), body == null ? 0 : body.quantity()));
        }

        /** UPI: the student paid by QR and enters the 12-digit UPI reference; an admin then verifies it. */
        @PostMapping("/purchases/{id}/utr")
        public ApiResponse<StudentPurchase> utr(Authentication auth, @PathVariable UUID id, @RequestBody UtrRequest body) {
            return ApiResponse.success(purchases.submitUtr(uid(auth), id, body == null ? null : body.utr()),
                    "Payment submitted. Your interviews are added once it is verified.");
        }

        @GetMapping
        public ApiResponse<Overview> overview(Authentication auth) {
            return ApiResponse.success(service.overview(uid(auth)));
        }

        @PostMapping("/sessions")
        public ApiResponse<SessionView> start(Authentication auth, @RequestBody StartRequest body) {
            return ApiResponse.success(service.start(uid(auth), body), "Interview started");
        }

        @GetMapping("/sessions/{id}")
        public ApiResponse<SessionView> session(Authentication auth, @PathVariable UUID id) {
            return ApiResponse.success(service.session(uid(auth), id));
        }

        /** Voice answer: 16 kHz mono WAV recorded in the browser. */
        @PostMapping(value = "/sessions/{id}/answers/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ApiResponse<SessionView> voice(Authentication auth, @PathVariable UUID id, @RequestParam UUID questionId,
                                              @RequestParam(required = false) Integer durationSeconds, @RequestPart("audio") MultipartFile audio) throws IOException {
            return ApiResponse.success(service.answerVoice(uid(auth), id, questionId, audio.getBytes(), durationSeconds));
        }

        /** Typed answer, or skip with empty text. */
        @PostMapping("/sessions/{id}/answers/text")
        public ApiResponse<SessionView> text(Authentication auth, @PathVariable UUID id, @RequestBody TextAnswer body) {
            return ApiResponse.success(service.answerText(uid(auth), id, body));
        }

        @PostMapping("/sessions/{id}/end")
        public ApiResponse<SessionView> end(Authentication auth, @PathVariable UUID id) {
            SessionView v = service.end(uid(auth), id);
            return ApiResponse.success(v, v == null ? "Interview cancelled. Your credit was returned." : "Interview ended");
        }

        @GetMapping("/sessions/{id}/report")
        public ApiResponse<ReportView> report(Authentication auth, @PathVariable UUID id) {
            return ApiResponse.success(service.report(uid(auth), id));
        }

        @PostMapping("/sessions/{id}/report/retry")
        public ApiResponse<ReportView> retry(Authentication auth, @PathVariable UUID id) {
            return ApiResponse.success(service.retryReport(uid(auth), id));
        }

        @GetMapping("/questions/{questionId}/speech")
        public ResponseEntity<byte[]> speech(Authentication auth, @PathVariable UUID questionId) {
            return wav(service.questionSpeech(uid(auth), questionId));
        }

        @GetMapping("/answers/{answerId}/audio")
        public ResponseEntity<byte[]> answerAudio(Authentication auth, @PathVariable UUID answerId) {
            return wav(service.answerAudio(uid(auth), answerId));
        }
    }

    /** Platform admin: give paid interview credits (until payments are added). */
    @Tag(name = "Admin - AI interview")
    @RestController
    @RequestMapping("/api/v1/admin/interview")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiredArgsConstructor
    public static class AdminInterviewController {
        private final AiInterviewService service;
        private final InterviewPurchaseService purchases;

        /** UPI payments to check against the bank statement (default: the ones waiting). */
        @GetMapping("/purchases")
        public ApiResponse<List<AdminPurchase>> purchases(@RequestParam(required = false) PurchaseStatus status) {
            return ApiResponse.success(purchases.forReview(status));
        }

        @PostMapping("/purchases/{id}/approve")
        public ApiResponse<AdminPurchase> approve(Authentication auth, @PathVariable UUID id) {
            return ApiResponse.success(purchases.approve(uid(auth), id), "Approved. The interviews were added.");
        }

        @PostMapping("/purchases/{id}/reject")
        public ApiResponse<AdminPurchase> reject(Authentication auth, @PathVariable UUID id, @RequestBody ReviewRequest body) {
            return ApiResponse.success(purchases.reject(uid(auth), id, body == null ? null : body.note()), "Rejected");
        }

        @PostMapping("/credits")
        public ApiResponse<GrantResult> grant(@RequestBody GrantRequest body) {
            GrantResult r = service.grant(body);
            return ApiResponse.success(r, r.studentName() + " now has " + r.paidBalance() + " paid interview(s)");
        }
    }
}
