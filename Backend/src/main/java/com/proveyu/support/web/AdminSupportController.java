package com.proveyu.support.web;

import com.proveyu.shared.response.ApiResponse;
import com.proveyu.support.application.SupportService;
import com.proveyu.support.domain.SupportModel.Reply;
import com.proveyu.support.domain.SupportModel.Status;
import com.proveyu.support.domain.SupportModel.StatusChange;
import com.proveyu.support.domain.SupportModel.Ticket;
import com.proveyu.support.domain.SupportModel.TicketPage;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** The ProveYu team's support inbox: messages from the college and student portals. */
@Tag(name = "Admin - Support")
@RestController
@RequestMapping("/api/v1/admin/support")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSupportController {

    private final SupportService service;

    @GetMapping
    public ApiResponse<TicketPage> list(@RequestParam(required = false) Status status, @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.success(service.list(status, q, page, size));
    }

    @GetMapping("/open-count")
    public ApiResponse<Map<String, Long>> openCount() {
        return ApiResponse.success(Map.of("open", service.openCount()));
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<Ticket> reply(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Reply r) {
        return ApiResponse.success(service.reply(UUID.fromString(auth.getName()), id, r), "Answer sent");
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Ticket> status(Authentication auth, @PathVariable UUID id, @Valid @RequestBody StatusChange r) {
        return ApiResponse.success(service.setStatus(UUID.fromString(auth.getName()), id, r.status()));
    }
}
