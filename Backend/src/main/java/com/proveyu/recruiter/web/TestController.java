package com.proveyu.recruiter.web;

import com.proveyu.recruiter.domain.RecruiterProfile;
import com.proveyu.recruiter.domain.RecruiterProfileRepository;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

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
