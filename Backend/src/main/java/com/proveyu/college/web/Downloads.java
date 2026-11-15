package com.proveyu.college.web;

import com.proveyu.college.domain.CollegeFile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class Downloads {
    private Downloads() {}

    static ResponseEntity<Resource> inline(Resource resource, CollegeFile f) {
        String safeName = f.getOriginalName().replaceAll("[\"\\r\\n]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
