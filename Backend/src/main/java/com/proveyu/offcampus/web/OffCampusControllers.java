package com.proveyu.offcampus.web;

import com.proveyu.college.application.CollegeFileService;
import com.proveyu.college.application.TabularExporter;
import com.proveyu.college.domain.CollegeFile;
import com.proveyu.college.web.Downloads;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.offcampus.application.OffCampusImportService;
import com.proveyu.offcampus.application.OffCampusNocService;
import com.proveyu.offcampus.application.OffCampusService;
import com.proveyu.offcampus.domain.OffCampusModel.Applicant;
import com.proveyu.offcampus.domain.OffCampusModel.AppStatus;
import com.proveyu.offcampus.domain.OffCampusModel.ApplyLink;
import com.proveyu.offcampus.domain.OffCampusModel.BulkStatus;
import com.proveyu.offcampus.domain.OffCampusModel.CollegeNoc;
import com.proveyu.offcampus.domain.OffCampusModel.ImportRun;
import com.proveyu.offcampus.domain.OffCampusModel.Interests;
import com.proveyu.offcampus.domain.OffCampusModel.JobField;
import com.proveyu.offcampus.domain.OffCampusModel.Noc;
import com.proveyu.offcampus.domain.OffCampusModel.NocStatus;
import com.proveyu.offcampus.domain.OffCampusModel.Source;
import com.proveyu.offcampus.domain.OffCampusModel.CollegeView;
import com.proveyu.offcampus.domain.OffCampusModel.Job;
import com.proveyu.offcampus.domain.OffCampusModel.JobPage;
import com.proveyu.offcampus.domain.OffCampusModel.JobRequest;
import com.proveyu.offcampus.domain.OffCampusModel.JobStatus;
import com.proveyu.offcampus.domain.OffCampusModel.StatusChange;
import com.proveyu.offcampus.domain.OffCampusModel.StudentJob;
import com.proveyu.offcampus.domain.OffCampusModel.StudentJobs;
import com.proveyu.offcampus.domain.OffCampusModel.StudentUpdate;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Off-campus jobs: the admin posts them, students apply on the company site, the placement cell watches. */
public final class OffCampusControllers {
    private OffCampusControllers() {}

    private static UUID uid(Authentication auth) {
        return UUID.fromString(auth.getName());
    }

    /** ProveYu team: post, edit, publish and close jobs; see who applied. */
    @Tag(name = "Admin - Off-campus jobs")
    @RestController
    @RequestMapping("/api/v1/admin/off-campus/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiredArgsConstructor
    public static class AdminOffCampusController {
        private final OffCampusService service;

        @GetMapping
        public ApiResponse<JobPage> list(@RequestParam(required = false) JobStatus status, @RequestParam(required = false) Source source,
                                         @RequestParam(required = false) JobField field, @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
            return ApiResponse.success(service.list(status, source, field, q, page, size));
        }

        @PostMapping
        public ResponseEntity<ApiResponse<Job>> create(Authentication auth, @Valid @RequestBody JobRequest r) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(uid(auth), r), "Job saved as a draft"));
        }

        @PutMapping("/{id}")
        public ApiResponse<Job> update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody JobRequest r) {
            return ApiResponse.success(service.update(uid(auth), id, r), "Job saved");
        }

        @PostMapping("/{id}/status")
        public ApiResponse<Job> status(Authentication auth, @PathVariable UUID id, @Valid @RequestBody StatusChange r) {
            return ApiResponse.success(service.setStatus(uid(auth), id, r.status(), r.sendNotification() == null || r.sendNotification()));
        }

        @PostMapping("/bulk-status")
        public ApiResponse<Map<String, Integer>> bulk(Authentication auth, @Valid @RequestBody BulkStatus r) {
            int done = service.bulkStatus(uid(auth), r.ids(), r.status());
            return ApiResponse.success(Map.of("updated", done), done + " job" + (done == 1 ? "" : "s") + " updated");
        }

        @PostMapping("/{id}/duplicate")
        public ApiResponse<Job> duplicate(Authentication auth, @PathVariable UUID id) {
            return ApiResponse.success(service.duplicate(uid(auth), id), "Copy saved as a draft");
        }

        @GetMapping("/{id}/applicants")
        public ApiResponse<List<Applicant>> applicants(@PathVariable UUID id) {
            return ApiResponse.success(service.applicants(id));
        }
    }

    /** ProveYu team: the nightly job import (Careerjet) and a manual run. */
    @Tag(name = "Admin - Off-campus jobs")
    @RestController
    @RequestMapping("/api/v1/admin/off-campus/import")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiredArgsConstructor
    public static class AdminOffCampusImportController {
        private final OffCampusImportService importer;

        @GetMapping
        public ApiResponse<ImportRun> last() {
            return ApiResponse.success(importer.last());
        }

        @PostMapping("/run")
        public ApiResponse<ImportRun> run() {
            ImportRun r = importer.run();
            return ApiResponse.success(r, r.configured() ? "Imported " + r.added() + " new jobs" : "Job import is not set up");
        }
    }

    /** Students: off-campus jobs for them, with eligibility; open the apply link; save; keep the status up to date. */
    @Tag(name = "Student - Off-campus jobs")
    @RestController
    @RequestMapping("/api/v1/college/student/off-campus")
    @PreAuthorize(CollegeAccess.STUDENT)
    @RequiredArgsConstructor
    public static class StudentOffCampusController {
        private final OffCampusService service;
        private final OffCampusNocService nocs;
        private final CollegeFileService files;

        @GetMapping
        public ApiResponse<StudentJobs> jobs(Authentication auth) {
            return ApiResponse.success(service.jobsFor(uid(auth)));
        }

        /** The fields the student wants to see first (software, data, mechanical, ...). Empty = all. */
        @PutMapping("/interests")
        public ApiResponse<List<JobField>> interests(Authentication auth, @Valid @RequestBody Interests r) {
            return ApiResponse.success(service.setInterests(uid(auth), r.fields()), "Interests saved");
        }

        @PostMapping("/{jobId}/click")
        public ApiResponse<ApplyLink> click(Authentication auth, @PathVariable UUID jobId) {
            return ApiResponse.success(service.click(uid(auth), jobId));
        }

        @PostMapping("/{jobId}/save")
        public ApiResponse<Map<String, Boolean>> save(Authentication auth, @PathVariable UUID jobId) {
            return ApiResponse.success(Map.of("saved", service.toggleSave(uid(auth), jobId)));
        }

        @PutMapping("/{jobId}/status")
        public ApiResponse<StudentJob> status(Authentication auth, @PathVariable UUID jobId, @Valid @RequestBody StudentUpdate r) {
            return ApiResponse.success(service.updateStatus(uid(auth), jobId, r), "Status updated");
        }

        @PostMapping(value = "/{jobId}/noc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<Noc>> requestNoc(Authentication auth, @PathVariable UUID jobId, @RequestPart("offerLetter") MultipartFile offerLetter,
                                                           @RequestParam(required = false) String note) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(nocs.request(uid(auth), jobId, offerLetter, note), "NOC request sent to your placement cell"));
        }

        /** kind = "noc" (the issued NOC) or "offer" (the offer letter the student attached). */
        @GetMapping("/noc/{nocId}/{kind}")
        public ResponseEntity<Resource> nocFile(Authentication auth, @PathVariable UUID nocId, @PathVariable String kind) {
            CollegeFile f = nocs.studentFile(uid(auth), nocId, kind);
            return Downloads.inline(files.load(f), f);
        }
    }

    /** Placement cell: its students' off-campus applications and offers. */
    @Tag(name = "College - Off-campus")
    @RestController
    @RequestMapping("/api/v1/college/off-campus")
    @RequiredArgsConstructor
    public static class CollegeOffCampusController {
        private final OffCampusService service;
        private final OffCampusNocService nocs;
        private final CollegeFileService files;
        private final TabularExporter exporter;

        @GetMapping("/nocs")
        @PreAuthorize(CollegeAccess.READ)
        public ApiResponse<List<CollegeNoc>> nocs(CollegeContext ctx, @RequestParam(required = false) NocStatus status) {
            return ApiResponse.success(nocs.forCollege(ctx, status));
        }

        /** status = APPROVED (optional signed PDF; without it the NOC is generated; ctcLpa = CTC from the letter) or REJECTED (note required). */
        @PostMapping(value = "/nocs/{id}/decision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize(CollegeAccess.WRITE)
        public ApiResponse<CollegeNoc> decide(CollegeContext ctx, @PathVariable UUID id, @RequestParam NocStatus status,
                                              @RequestParam(required = false) String note, @RequestPart(value = "file", required = false) MultipartFile file,
                                              @RequestParam(required = false) java.math.BigDecimal ctcLpa) {
            CollegeNoc r = nocs.decide(ctx, id, status, note, file, ctcLpa);
            return ApiResponse.success(r, status == NocStatus.REJECTED ? "NOC request rejected"
                    : r.placementRecorded() ? "NOC issued and the placement recorded" : "NOC issued");
        }

        @GetMapping("/nocs/{id}/{kind}")
        @PreAuthorize(CollegeAccess.READ)
        public ResponseEntity<Resource> nocFile(CollegeContext ctx, @PathVariable UUID id, @PathVariable String kind) {
            CollegeFile f = nocs.collegeFile(ctx, id, kind);
            return Downloads.inline(files.load(f), f);
        }

        @GetMapping
        @PreAuthorize(CollegeAccess.READ)
        public ApiResponse<CollegeView> list(CollegeContext ctx, @RequestParam(required = false) AppStatus status, @RequestParam(required = false) String q) {
            return ApiResponse.success(service.forCollege(ctx, status, q));
        }

        @GetMapping("/export")
        @PreAuthorize(CollegeAccess.READ)
        public ResponseEntity<byte[]> export(CollegeContext ctx, @RequestParam(defaultValue = "xlsx") String format,
                                             @RequestParam(required = false) AppStatus status) {
            List<List<Object>> rows = new ArrayList<>();
            service.forCollege(ctx, status, null).rows().forEach(r -> rows.add(List.of(s(r.rollNumber()), r.studentName(), s(r.branch()),
                    r.batchYear() == null ? "" : r.batchYear(), r.companyName(), r.roleTitle(), r.jobType().name().replace('_', ' '), r.status().name(),
                    r.offerCtcLpa() == null ? "" : r.offerCtcLpa(), r.appliedAt() == null ? "" : r.appliedAt().toString(), s(r.note()))));
            return exporter.export(new TabularExporter.Table("Off-campus", List.of("Roll No", "Student", "Branch", "Batch", "Company", "Role", "Type",
                    "Status", "Offer CTC (LPA)", "Applied at", "Note"), rows), format, "off-campus").toResponse();
        }

        private static String s(String v) {
            return v == null ? "" : v;
        }
    }
}
