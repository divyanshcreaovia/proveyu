package com.proveyu.student.codeground;

import com.proveyu.shared.response.ApiResponse;
import com.proveyu.student.codeground.CodeGroundContent.Arg;
import com.proveyu.student.codeground.CodeGroundContent.Spec;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Platform admin: Code ground topics, problems and the problem of the day. */
@Tag(name = "Admin - Code ground")
@RestController
@RequestMapping("/api/v1/admin/code-ground")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CodeGroundAdminController {

    public record TemplateRequest(List<Arg> args, String returnType) {}

    public record HiddenRequest(boolean hidden) {}

    public record DayRequest(String problemKey) {}

    private final CodeGroundAuthoring authoring;
    private final CodeGroundSchedule schedule;

    private static UUID uid(Authentication a) {
        return UUID.fromString(a.getName());
    }

    /** Every topic and problem with its status. */
    @GetMapping
    public ApiResponse<List<CodeGroundAuthoring.TopicSummary>> catalogue() {
        return ApiResponse.success(authoring.catalogue());
    }

    @PutMapping("/topics")
    public ApiResponse<Void> saveTopic(Authentication auth, @RequestBody CodeGroundAuthoring.TopicInput body) {
        authoring.saveTopic(uid(auth), body);
        return ApiResponse.success(null, "Topic saved");
    }

    @DeleteMapping("/topics/{key}")
    public ApiResponse<Void> deleteTopic(Authentication auth, @PathVariable String key) {
        authoring.deleteTopic(uid(auth), key);
        return ApiResponse.success(null, "Topic deleted");
    }

    @GetMapping("/problems/{key}")
    public ApiResponse<CodeGroundAuthoring.Authored> problem(@PathVariable String key) {
        return ApiResponse.success(authoring.authored(key));
    }

    /** Creates or updates a draft. */
    @PutMapping("/problems")
    public ApiResponse<Void> saveProblem(Authentication auth, @RequestBody Spec body) {
        authoring.saveProblem(uid(auth), body);
        return ApiResponse.success(null, "Saved as draft");
    }

    /** Runs the reference solution on every test; publishes only if all match. */
    @PostMapping("/problems/{key}/publish")
    public ApiResponse<CodeGroundAuthoring.Published> publish(Authentication auth, @PathVariable String key) {
        return ApiResponse.success(authoring.publish(uid(auth), key));
    }

    @PostMapping("/problems/{key}/unpublish")
    public ApiResponse<Void> unpublish(Authentication auth, @PathVariable String key) {
        authoring.unpublish(uid(auth), key);
        return ApiResponse.success(null, "Unpublished");
    }

    @DeleteMapping("/problems/{key}")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable String key) {
        authoring.delete(uid(auth), key);
        return ApiResponse.success(null, "Deleted");
    }

    /** Hides or shows a built-in problem. */
    @PutMapping("/problems/{key}/hidden")
    public ApiResponse<Void> hidden(Authentication auth, @PathVariable String key, @RequestBody HiddenRequest body) {
        authoring.setHidden(uid(auth), key, body.hidden());
        return ApiResponse.success(null, body.hidden() ? "Hidden from students" : "Shown to students");
    }

    @PostMapping("/templates")
    public ApiResponse<List<CodeGroundAuthoring.Template>> templates(@RequestBody TemplateRequest body) {
        return ApiResponse.success(authoring.templates(body.args(), body.returnType()));
    }

    /** Topics and problems from a JSON file, all saved as drafts (all or nothing). */
    @PostMapping("/upload")
    public ApiResponse<CodeGroundAuthoring.Uploaded> upload(Authentication auth, @RequestBody CodeGroundAuthoring.Upload body) {
        return ApiResponse.success(authoring.upload(uid(auth), body));
    }

    /** An example of the upload file. */
    @GetMapping(value = "/sample", produces = "application/json")
    public String sample() throws java.io.IOException {
        try (var in = new org.springframework.core.io.ClassPathResource("codeground-sample/sample.json").getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/daily")
    public ApiResponse<List<CodeGroundSchedule.Day>> daily(@RequestParam(defaultValue = "14") int days) {
        return ApiResponse.success(schedule.upcoming(days));
    }

    @PutMapping("/daily/{date}")
    public ApiResponse<Void> setDaily(Authentication auth, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestBody DayRequest body) {
        schedule.set(date, body.problemKey());
        return ApiResponse.success(null, "Problem of the day set");
    }

    @DeleteMapping("/daily/{date}")
    public ApiResponse<Void> clearDaily(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        schedule.clear(date);
        return ApiResponse.success(null, "Back to automatic");
    }
}
