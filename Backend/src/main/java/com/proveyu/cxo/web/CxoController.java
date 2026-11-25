package com.proveyu.cxo.web;

import com.proveyu.cxo.application.CxoDashboardService;
import com.proveyu.cxo.domain.CxoModel.*;
import com.proveyu.cxo.security.CxoContext;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/** CXO portal API. Every call is scoped to the signed-in CXO's college (from {@link CxoContext}, never from the request). */
@Tag(name = "CXO portal")
@RestController
@RequestMapping("/api/v1/cxo")
@PreAuthorize("hasRole('CXO')")
@RequiredArgsConstructor
public class CxoController {

    private final CxoDashboardService service;

    @GetMapping("/me")
    public ApiResponse<Me> me(CxoContext ctx) {
        return ApiResponse.success(service.me(ctx), "OK");
    }

    @Operation(summary = "Metrics the AI can use, the college's branches and batches, and example questions")
    @GetMapping("/catalog")
    public ApiResponse<Catalog> catalog(CxoContext ctx) {
        return ApiResponse.success(service.catalog(ctx), "OK");
    }

    @Operation(summary = "Build a dashboard from a question, streamed as Server-Sent Events: status, plan, widget (one each), insight, done | error")
    @PostMapping(value = "/dashboards/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(CxoContext ctx, @Valid @RequestBody GenerateRequest req, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no"); // nginx: pass events through as they are sent (live build)
        response.setHeader("Cache-Control", "no-cache");
        return service.generate(ctx, req);
    }

    @Operation(summary = "Fresh numbers for a dashboard spec (used for live auto-refresh)")
    @PostMapping("/dashboards/run")
    public ApiResponse<List<WidgetData>> run(CxoContext ctx, @Valid @RequestBody RunRequest req) {
        return ApiResponse.success(service.run(ctx, req.spec()), "OK");
    }

    @Operation(summary = "AI summary of a dashboard's current numbers (cached while the numbers stay the same)")
    @PostMapping("/dashboards/summary")
    public ApiResponse<Insight> summary(CxoContext ctx, @Valid @RequestBody SummaryRequest req) {
        return ApiResponse.success(service.summary(ctx, req), "OK");
    }

    @GetMapping("/dashboards")
    public ApiResponse<List<DashboardSummary>> list(CxoContext ctx) {
        return ApiResponse.success(service.list(ctx), "OK");
    }

    @PostMapping("/dashboards")
    public ResponseEntity<ApiResponse<DashboardDetail>> save(CxoContext ctx, @Valid @RequestBody SaveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.save(ctx, req), "Dashboard saved"));
    }

    @GetMapping("/dashboards/{id}")
    public ApiResponse<DashboardDetail> get(CxoContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(service.get(ctx, id), "OK");
    }

    @PutMapping("/dashboards/{id}")
    public ApiResponse<DashboardDetail> update(CxoContext ctx, @PathVariable UUID id, @Valid @RequestBody SaveRequest req) {
        return ApiResponse.success(service.update(ctx, id, req), "Dashboard updated");
    }

    @PostMapping("/dashboards/{id}/pin")
    public ApiResponse<Void> pin(CxoContext ctx, @PathVariable UUID id, @RequestBody PinRequest req) {
        service.pin(ctx, id, req.pinned());
        return ApiResponse.success(null, req.pinned() ? "Pinned" : "Unpinned");
    }

    @DeleteMapping("/dashboards/{id}")
    public ApiResponse<Void> delete(CxoContext ctx, @PathVariable UUID id) {
        service.delete(ctx, id);
        return ApiResponse.success(null, "Dashboard deleted");
    }
}
