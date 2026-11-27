package com.proveyu.cxo.web;

import com.proveyu.cxo.application.CxoShareService;
import com.proveyu.cxo.domain.CxoModel.SharedBoard;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A shared CXO dashboard, opened with its link: no account, read only, never indexed or cached. */
@Tag(name = "CXO shared link")
@RestController
@RequestMapping("/api/v1/public/cxo")
@RequiredArgsConstructor
public class CxoPublicController {

    private final CxoShareService shares;

    @Operation(summary = "Open a shared dashboard (fresh numbers, no student names)")
    @GetMapping("/share/{token}")
    public ResponseEntity<ApiResponse<SharedBoard>> open(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer").body(ApiResponse.success(shares.open(token), "OK"));
    }
}
