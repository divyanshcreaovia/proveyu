package com.proveyu.student.prep.web;

import com.proveyu.shared.response.ApiResponse;
import com.proveyu.student.prep.application.CodeRunnerHealthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform admin: is the code runner (Judge0) reachable and authorised? */
@Tag(name = "Admin - code runner")
@RestController
@RequestMapping("/api/v1/admin/code-runner")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCodeRunnerController {

    private final CodeRunnerHealthService health;

    @GetMapping("/health")
    public ApiResponse<CodeRunnerHealthService.Report> health() {
        return ApiResponse.success(health.check());
    }
}
