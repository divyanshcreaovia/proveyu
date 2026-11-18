package com.proveyu.college.web;

import com.proveyu.college.domain.CollegeFile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Inline download of a stored college file (used by the college, student and company controllers). */
public final class Downloads {
    private Downloads() {}

    public static ResponseEntity<Resource> inline(Resource resource, CollegeFile f) {
        String safeName = f.getOriginalName().replaceAll("[\"\\r\\n]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
