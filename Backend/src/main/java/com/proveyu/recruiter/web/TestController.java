package com.proveyu.recruiter.web;

import com.proveyu.recruiter.domain.RecruiterProfile;
import com.proveyu.recruiter.domain.RecruiterProfileRepository;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Debug endpoint: only registered under the "dev" profile and only reachable by ADMIN (see SecurityConfig). */
@org.springframework.context.annotation.Profile("dev")
@RestController
@RequestMapping("/api/v1/test")
public class TestController {
    private final RecruiterProfileRepository rp;
    public TestController(RecruiterProfileRepository rp) { this.rp = rp; }

    @GetMapping("/{id}")
    public Object test(@PathVariable String id) {
        UUID uuid = UUID.fromString(id);
        return rp.findById(uuid).orElse(null);
    }
}
