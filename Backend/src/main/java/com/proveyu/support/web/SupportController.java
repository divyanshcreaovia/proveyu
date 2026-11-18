package com.proveyu.support.web;

import com.proveyu.shared.response.ApiResponse;
import com.proveyu.support.application.SupportService;
import com.proveyu.support.domain.SupportModel.NewTicket;
import com.proveyu.support.domain.SupportModel.Ticket;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Help & support for students and college staff: write to the ProveYu team and read the answers. */
@Tag(name = "Support")
@RestController
@RequestMapping("/api/v1/support/tickets")
@PreAuthorize("hasAnyRole('CANDIDATE','COLLEGE_ADMIN','COLLEGE_COORDINATOR','COLLEGE_VIEWER')")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService service;

    @PostMapping
    public ResponseEntity<ApiResponse<Ticket>> create(Authentication auth, @Valid @RequestBody NewTicket r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(UUID.fromString(auth.getName()), r), "Your message was sent to the ProveYu team"));
    }

    @GetMapping
    public ApiResponse<List<Ticket>> mine(Authentication auth) {
        return ApiResponse.success(service.mine(UUID.fromString(auth.getName())));
    }
}
