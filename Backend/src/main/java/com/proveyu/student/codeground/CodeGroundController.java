package com.proveyu.student.codeground;

import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Student - Code ground")
@RestController
@RequestMapping("/api/v1/college/student/code-ground")
@PreAuthorize("hasRole('CANDIDATE')")
@RequiredArgsConstructor
public class CodeGroundController {

    /** language: javascript, python, java or cpp (javascript when left out). */
    public record CodeRequest(String language, String code) {}

    /** input: the student's own input; left out = the example. */
    public record RunRequest(String language, String code, String input) {}

    private final CodeGroundService service;
    private final CodeGroundLeaderboard leaderboard;

    private static UUID uid(Authentication a) {
        return UUID.fromString(a.getName());
    }

    @GetMapping
    public ApiResponse<CodeGroundService.Overview> overview(Authentication auth) {
        return ApiResponse.success(service.overview(uid(auth)));
    }

    /** period: week (default) or all. scope: college (default), branch or batch of the student. */
    @GetMapping("/leaderboard")
    public ApiResponse<CodeGroundLeaderboard.Board> leaderboard(Authentication auth, @RequestParam(required = false) String period,
            @RequestParam(required = false) String scope) {
        return ApiResponse.success(leaderboard.forStudent(uid(auth), period, scope));
    }

    /** Problems solved per day over the last year, and the streaks. */
    @GetMapping("/activity")
    public ApiResponse<CodeGroundService.Activity> activity(Authentication auth) {
        return ApiResponse.success(service.activity(uid(auth)));
    }

    /** This student's last submissions of one problem, newest first. */
    @GetMapping("/problems/{key}/submissions")
    public ApiResponse<java.util.List<CodeGroundHistory.Submission>> submissions(Authentication auth, @PathVariable String key) {
        return ApiResponse.success(service.submissions(uid(auth), key));
    }

    /** All submissions with filters. days (1 = today) or from / to are India dates. */
    @GetMapping("/submissions")
    public ApiResponse<CodeGroundService.MySubmissions> mySubmissions(Authentication auth, @RequestParam(required = false) Integer days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String verdict, @RequestParam(required = false) String language,
            @RequestParam(required = false) String difficulty, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean stuck, @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(service.mySubmissions(uid(auth),
                new CodeGroundService.SubmissionFilter(days, from, to, verdict, language, difficulty, q, stuck, page)));
    }

    @GetMapping("/submissions/{id}")
    public ApiResponse<CodeGroundService.SubmissionCode> submissionCode(Authentication auth, @PathVariable long id) {
        return ApiResponse.success(service.submissionCode(uid(auth), id));
    }

    @GetMapping("/topics/{key}/guide")
    public ApiResponse<CodeGroundService.Guide> guide(Authentication auth, @PathVariable String key) {
        return ApiResponse.success(service.guide(uid(auth), key));
    }

    @PostMapping("/topics/{key}/guide/complete")
    public ApiResponse<Void> completeGuide(Authentication auth, @PathVariable String key) {
        service.completeGuide(uid(auth), key);
        return ApiResponse.success(null, "Guide marked as read");
    }

    @GetMapping("/problems/{key}")
    public ApiResponse<CodeGroundService.ProblemView> problem(Authentication auth, @PathVariable String key) {
        return ApiResponse.success(service.problem(uid(auth), key));
    }

    @PostMapping("/problems/{key}/run")
    public ApiResponse<CodeGroundService.Sample> run(Authentication auth, @PathVariable String key, @RequestBody RunRequest body) {
        return ApiResponse.success(service.run(uid(auth), key, body.language(), body.code(), body.input()));
    }

    /** All the tests, visible and hidden: only this marks a problem solved. */
    @PostMapping("/problems/{key}/submit")
    public ApiResponse<CodeGroundService.RunResult> submit(Authentication auth, @PathVariable String key, @RequestBody CodeRequest body) {
        return ApiResponse.success(service.submit(uid(auth), key, body.language(), body.code()));
    }

    /** Saves the code being written; empty code removes it. */
    @PutMapping("/problems/{key}/draft")
    public ApiResponse<Void> draft(Authentication auth, @PathVariable String key, @RequestBody CodeRequest body) {
        service.saveDraft(uid(auth), key, body.language(), body.code());
        return ApiResponse.success(null, "Saved");
    }
}
