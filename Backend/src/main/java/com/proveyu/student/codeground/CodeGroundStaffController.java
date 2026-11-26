package com.proveyu.student.codeground;

import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The Code ground ranking of the staff member's own college. */
@Tag(name = "College - Code ground")
@RestController
@RequestMapping("/api/v1/college/code-ground")
@RequiredArgsConstructor
public class CodeGroundStaffController {

    private final CodeGroundLeaderboard leaderboard;

    @GetMapping("/leaderboard")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<CodeGroundLeaderboard.StaffBoard> leaderboard(CollegeContext ctx, @RequestParam(required = false) String period,
            @RequestParam(required = false) UUID branchId, @RequestParam(required = false) Integer batchYear) {
        return ApiResponse.success(leaderboard.forStaff(ctx, period, branchId, batchYear));
    }
}
