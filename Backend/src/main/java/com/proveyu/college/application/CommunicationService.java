package com.proveyu.college.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.StudentStatus;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.notification.application.NotificationService;
import com.proveyu.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Announcements to filtered student groups, global search and chat contact discovery. */
@Service
@RequiredArgsConstructor
public class CommunicationService {

    public record AudienceFilter(List<Integer> batchYears, List<UUID> branchIds, List<StudentStatus> statuses, Boolean placed, UUID driveId) {}

    public record AnnouncementRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 10000) String body,
                                      @Valid AudienceFilter filter, boolean sendEmail) {}

    public record AnnouncementResponse(UUID id, String title, String body, AudienceFilter filter, int recipientCount, boolean sendEmail,
                                       String createdByName, Instant createdAt) {}

    public record SearchHit(UUID id, String type, String title, String subtitle) {}

    public record SearchResult(List<SearchHit> students, List<SearchHit> companies, List<SearchHit> drives) {}

    public record ChatContact(UUID userId, String name, String type, String subtitle) {}

    private final AnnouncementRepository announcementRepository;
    private final CollegeStudentRepository studentRepository;
    private final CollegeCompanyRepository companyRepository;
    private final CollegeDriveRepository driveRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CollegeDirectory directory;
    private final NotificationService notificationService;
    private final CollegeMailService mailService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<CollegeStudent> audience(CollegeContext ctx, AudienceFilter f) {
        Set<UUID> driveApplicants = f == null || f.driveId() == null ? null : applicationRepository.findAllByCollegeIdAndDriveId(ctx.collegeId(), f.driveId())
                .stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet());
        return studentRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(s -> ctx.canAccessBranch(s.getBranchId()))
                .filter(s -> f == null || f.batchYears() == null || f.batchYears().isEmpty() || f.batchYears().contains(s.getBatchYear()))
                .filter(s -> f == null || f.branchIds() == null || f.branchIds().isEmpty() || f.branchIds().contains(s.getBranchId()))
                .filter(s -> f == null || f.statuses() == null || f.statuses().isEmpty() || f.statuses().contains(s.getStatus()))
                .filter(s -> f == null || f.placed() == null || (s.getStatus() == StudentStatus.PLACED) == f.placed())
                .filter(s -> driveApplicants == null || driveApplicants.contains(s.getId()))
                .toList();
    }

    @Transactional
    public AnnouncementResponse announce(CollegeContext ctx, AnnouncementRequest r) {
        List<CollegeStudent> audience = audience(ctx, r.filter());
        String bodyHtml = "<h3>" + HtmlUtils.htmlEscape(r.title()) + "</h3><p>" + HtmlUtils.htmlEscape(r.body()).replace("\n", "<br>") + "</p>";
        for (CollegeStudent s : audience) {
            notificationService.notify(s.getUserId(), "ANNOUNCEMENT", r.title() + ": " + (r.body().length() > 300 ? r.body().substring(0, 297) + "..." : r.body()));
            if (r.sendEmail()) {
                mailService.send(ctx.collegeId(), "ANNOUNCEMENT", s.getEmail(), Map.of("studentName", CollegeDirectory.fullName(s),
                        "title", r.title(), "bodyHtml", bodyHtml));
            }
        }
        Announcement a = announcementRepository.save(Announcement.builder().collegeId(ctx.collegeId()).title(r.title()).body(r.body())
                .filterJson(toJson(r.filter())).recipientCount(audience.size()).sendEmail(r.sendEmail()).createdBy(ctx.userId()).build());
        auditService.log(ctx, "ANNOUNCEMENT_SENT", "ANNOUNCEMENT", a.getId(), "Sent '" + r.title() + "' to " + audience.size() + " student(s)", null);
        return toResponse(a, Map.of(ctx.userId(), ctx.actorName()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> announcements(CollegeContext ctx, int page, int size) {
        var result = announcementRepository.findAllByCollegeIdOrderByCreatedAtDesc(ctx.collegeId(), PageRequest.of(Math.max(page, 0), Math.min(size, 100)));
        Map<UUID, String> names = userRepository.findAllByIdIn(result.getContent().stream().map(Announcement::getCreatedBy).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return PageResponse.of(result, a -> toResponse(a, names));
    }

    @Transactional(readOnly = true)
    public SearchResult search(CollegeContext ctx, String q) {
        if (q == null || q.trim().length() < 2) return new SearchResult(List.of(), List.of(), List.of());
        String n = q.trim().toLowerCase(Locale.ROOT);
        var branches = directory.branches(ctx.collegeId());
        var students = studentRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(s -> ctx.canAccessBranch(s.getBranchId()))
                .filter(s -> s.getRollNumber().toLowerCase(Locale.ROOT).contains(n) || CollegeDirectory.fullName(s).toLowerCase(Locale.ROOT).contains(n)
                        || s.getEmail().contains(n)).limit(6)
                .map(s -> new SearchHit(s.getId(), "student", CollegeDirectory.fullName(s), s.getRollNumber() + " · "
                        + (s.getBranchId() != null && branches.containsKey(s.getBranchId()) ? branches.get(s.getBranchId()).getCode() : "-") + " · " + s.getBatchYear()))
                .toList();
        var companies = companyRepository.findAllByCollegeIdAndDeletedAtIsNullOrderByNameAsc(ctx.collegeId()).stream()
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(n)).limit(5)
                .map(c -> new SearchHit(c.getId(), "company", c.getName(), Objects.toString(c.getIndustry(), "Company"))).toList();
        var drives = driveRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(d -> d.getTitle().toLowerCase(Locale.ROOT).contains(n) || d.getRoleTitle().toLowerCase(Locale.ROOT).contains(n)).limit(5)
                .map(d -> new SearchHit(d.getId(), "drive", d.getTitle(), d.getRoleTitle() + " · " + d.getStatus())).toList();
        return new SearchResult(students, companies, drives);
    }

    /** People a college staff member can chat with: own students with accounts, linked recruiters and team mates. */
    @Transactional(readOnly = true)
    public List<ChatContact> chatContacts(CollegeContext ctx, String q) {
        String n = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<ChatContact> out = new ArrayList<>();
        memberRepository.findAllByCollegeIdOrderByCreatedAtAsc(ctx.collegeId()).stream().filter(m -> !m.getUserId().equals(ctx.userId()))
                .forEach(m -> userRepository.findById(m.getUserId()).ifPresent(u -> out.add(new ChatContact(u.getId(), u.getFullName(), "TEAM",
                        m.getRole().name().replace("COLLEGE_", "")))));
        Set<UUID> recruiterIds = companyRepository.findAllByCollegeIdAndDeletedAtIsNullOrderByNameAsc(ctx.collegeId()).stream()
                .map(CollegeCompany::getLinkedRecruiterId).filter(Objects::nonNull).collect(Collectors.toSet());
        userRepository.findAllByIdIn(recruiterIds).forEach(u -> out.add(new ChatContact(u.getId(), u.getFullName(), "RECRUITER", u.getEmail())));
        studentRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(s -> s.getUserId() != null && ctx.canAccessBranch(s.getBranchId()))
                .filter(s -> n.isEmpty() || CollegeDirectory.fullName(s).toLowerCase(Locale.ROOT).contains(n) || s.getRollNumber().toLowerCase(Locale.ROOT).contains(n))
                .limit(200).forEach(s -> out.add(new ChatContact(s.getUserId(), CollegeDirectory.fullName(s), "STUDENT", s.getRollNumber())));
        return out.stream().filter(c -> n.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(n) || Objects.toString(c.subtitle(), "").toLowerCase(Locale.ROOT).contains(n)).toList();
    }

    private AnnouncementResponse toResponse(Announcement a, Map<UUID, String> names) {
        AudienceFilter f = null;
        try {
            if (a.getFilterJson() != null) f = objectMapper.readValue(a.getFilterJson(), AudienceFilter.class);
        } catch (Exception ignored) {
            // older rows with unreadable filters are shown without filter details
        }
        return new AnnouncementResponse(a.getId(), a.getTitle(), a.getBody(), f, a.getRecipientCount(), a.isSendEmail(),
                names.getOrDefault(a.getCreatedBy(), "-"), a.getCreatedAt());
    }

    private String toJson(Object o) {
        try {
            return o == null ? null : objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}
