package com.proveyu.offcampus.application;

import com.proveyu.college.application.AuditService;
import com.proveyu.college.application.CollegeFileService;
import com.proveyu.college.application.CollegeDirectory;
import com.proveyu.college.application.EligibilityEngine;
import com.proveyu.college.application.StudentService;
import com.proveyu.college.domain.CollegeCompany;
import com.proveyu.college.domain.CollegeDrive;
import com.proveyu.college.domain.CollegeEnums.OfferStatus;
import com.proveyu.college.domain.CollegeFile;
import com.proveyu.college.domain.CollegeOffer;
import com.proveyu.college.domain.CollegeStudent;
import com.proveyu.college.domain.CollegeTier;
import com.proveyu.college.infrastructure.CollegeCompanyRepository;
import com.proveyu.college.infrastructure.CollegeOfferRepository;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.notification.application.NotificationService;
import com.proveyu.offcampus.application.OffCampusService.Me;
import com.proveyu.offcampus.domain.OffCampusModel.CollegeNoc;
import com.proveyu.offcampus.domain.OffCampusModel.Job;
import com.proveyu.offcampus.domain.OffCampusModel.JobType;
import com.proveyu.offcampus.domain.OffCampusModel.Noc;
import com.proveyu.offcampus.domain.OffCampusModel.NocStatus;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.proveyu.offcampus.application.OffCampusService.instant;
import static com.proveyu.offcampus.application.OffCampusService.name;

/**
 * NOC (No Objection Certificate) for off-campus offers. A student whose status for a job is OFFER attaches the offer letter and asks
 * the placement cell; the TPO or a coordinator approves (ProveYu generates the NOC PDF, or they upload a signed one) or rejects with a
 * reason. The student is notified and downloads the NOC from the portal. One open (pending or approved) request per job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OffCampusNocService {

    private static final String COLLEGE_SQL = """
            select n.*, s.first_name, s.last_name, s.roll_number, s.batch_year, s.branch_id, b.name branch, j.company_name, j.role_title, j.job_type,
                   a.offer_ctc_lpa, f.original_name offer_name
            from off_campus_noc_requests n join college_students s on s.id = n.student_id left join college_branches b on b.id = s.branch_id
                 join off_campus_jobs j on j.id = n.job_id join off_campus_applications a on a.id = n.application_id
                 left join college_files f on f.id = n.offer_file_id
            """;

    private final JdbcTemplate jdbc;
    private final OffCampusService offCampus;
    private final CollegeFileService files;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CollegeDirectory directory;
    private final StudentService studentService;
    private final CollegeCompanyRepository companies;
    private final CollegeOfferRepository offers;

    // ================================================================== student

    @Transactional
    public Noc request(UUID userId, UUID jobId, MultipartFile offerLetter, String note) {
        Me me = offCampus.me(userId);
        Job job = offCampus.jobForStudent(me, jobId);
        Map<String, Object> app = jdbc.queryForList("select id, status from off_campus_applications where job_id = ? and student_id = ?",
                jobId.toString(), me.id().toString()).stream().findFirst().orElse(null);
        if (app == null || !"OFFER".equals(app.get("status"))) {
            throw new DomainException("Set your status to \"Got an offer\" first", HttpStatus.CONFLICT, "NO_OFFER");
        }
        Integer open = jdbc.queryForObject("select count(*) from off_campus_noc_requests where application_id = ? and status in ('PENDING', 'APPROVED')",
                Integer.class, app.get("id"));
        if (open != null && open > 0) {
            throw new DomainException("You already have a NOC request for this offer", HttpStatus.CONFLICT, "NOC_EXISTS");
        }
        UUID collegeId = me.facts().collegeId();
        CollegeFile f = files.store(collegeId, userId, offerLetter, "OFF_CAMPUS_OFFER_LETTER", CollegeFileService.DOC_EXT);
        UUID id = UUID.randomUUID();
        String cleanNote = note == null || note.isBlank() ? null : note.trim().substring(0, Math.min(note.trim().length(), 1000));
        jdbc.update("""
                insert into off_campus_noc_requests (id, application_id, job_id, student_id, user_id, college_id, offer_file_id, student_note)
                values (?, ?, ?, ?, ?, ?, ?, ?)""", id.toString(), app.get("id"), jobId.toString(), me.id().toString(), userId.toString(),
                collegeId.toString(), f.getId().toString(), cleanNote);
        String msg = "NOC request: " + me.name() + " (" + me.rollNumber() + ") needs a NOC for the " + job.companyName() + " · " + job.roleTitle()
                + " offer. Open Off-campus → NOC requests.";
        for (String staff : jdbc.queryForList("""
                select user_id from college_members where college_id = ? and status = 'ACTIVE' and role in ('COLLEGE_ADMIN', 'COLLEGE_COORDINATOR')""",
                String.class, collegeId.toString())) {
            notifications.notify(UUID.fromString(staff), "OFF_CAMPUS", msg);
        }
        return studentNoc(id);
    }

    /** The student's own offer letter ("offer") or the NOC ("noc"). */
    public CollegeFile studentFile(UUID userId, UUID nocId, String kind) {
        Me me = offCampus.me(userId);
        Map<String, Object> n = jdbc.queryForList("select college_id, offer_file_id, noc_file_id, status from off_campus_noc_requests where id = ? and student_id = ?",
                nocId.toString(), me.id().toString()).stream().findFirst().orElseThrow(OffCampusNocService::notFound);
        return fileOf(n, kind);
    }

    // ================================================================== placement cell

    public List<CollegeNoc> forCollege(CollegeContext ctx, NocStatus status) {
        StringBuilder where = new StringBuilder(" where n.college_id = ?");
        List<Object> args = new ArrayList<>(List.of(ctx.collegeId().toString()));
        scope(ctx, where, args);
        if (status != null) { where.append(" and n.status = ?"); args.add(status.name()); }
        return jdbc.query(COLLEGE_SQL + where + " order by field(n.status, 'PENDING', 'APPROVED', 'REJECTED'), n.created_at desc limit 1000",
                (rs, i) -> new CollegeNoc(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("application_id")), name(rs),
                        rs.getString("roll_number"), rs.getString("branch"), (Integer) rs.getObject("batch_year"), rs.getString("company_name"),
                        rs.getString("role_title"), JobType.valueOf(rs.getString("job_type")), rs.getBigDecimal("offer_ctc_lpa"), rs.getString("offer_name"),
                        NocStatus.valueOf(rs.getString("status")), rs.getString("student_note"), rs.getString("decision_note"),
                        rs.getString("noc_file_id") != null, rs.getBoolean("noc_generated"), rs.getString("decided_by_name"),
                        instant(rs.getTimestamp("decided_at")), instant(rs.getTimestamp("created_at")), rs.getString("college_offer_id") != null), args.toArray());
    }

    /**
     * Approve or reject a pending request. Approving with a signed PDF stores it as the NOC; approving without one generates the NOC.
     * Approving a full-time job or an internship with a pre-placement offer also records an ACCEPTED off-campus offer in the college's
     * offers (CTC confirmed by the placement cell from the letter), so the student becomes PLACED and the offer counts in the dashboard,
     * reports and placement policy. A plain internship does not change placement status. Rejecting needs a reason the student sees.
     */
    @Transactional
    public CollegeNoc decide(CollegeContext ctx, UUID id, NocStatus decision, String note, MultipartFile signedNoc, BigDecimal confirmedCtc) {
        if (decision == NocStatus.PENDING) throw new DomainException("Choose approve or reject", HttpStatus.BAD_REQUEST, "BAD_DECISION");
        Map<String, Object> n = row(ctx, id);
        if (!"PENDING".equals(n.get("status"))) {
            throw new DomainException("This request was already " + n.get("status").toString().toLowerCase(), HttpStatus.CONFLICT, "NOC_DECIDED");
        }
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 1000) throw new DomainException("Keep the note under 1000 characters", HttpStatus.BAD_REQUEST, "NOTE_TOO_LONG");
        if (decision == NocStatus.REJECTED && cleanNote == null) {
            throw new DomainException("Tell the student why the NOC is not given", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }
        String student = nameOf(n), company = (String) n.get("company_name"), role = (String) n.get("role_title");
        boolean placement = decision == NocStatus.APPROVED && JobType.valueOf((String) n.get("job_type")) != JobType.INTERNSHIP;
        BigDecimal ctc = confirmedCtc != null ? confirmedCtc : (BigDecimal) n.get("offer_ctc_lpa");
        if (placement && (ctc == null || ctc.signum() <= 0 || ctc.compareTo(BigDecimal.valueOf(500)) > 0)) {
            throw new DomainException("Enter the CTC (LPA) from the offer letter: approving records the placement", HttpStatus.BAD_REQUEST, "CTC_REQUIRED");
        }
        if (placement) n.put("offer_ctc_lpa", ctc); // the NOC prints the confirmed CTC
        String fileId = null;
        boolean generated = false;
        if (decision == NocStatus.APPROVED) {
            if (signedNoc != null && !signedNoc.isEmpty()) {
                fileId = files.store(ctx.collegeId(), ctx.userId(), signedNoc, "OFF_CAMPUS_NOC", CollegeFileService.PDF_EXT).getId().toString();
            } else {
                byte[] pdf = generate(ctx, id, n, cleanNote);
                String fileName = "NOC-" + n.get("roll_number").toString().replaceAll("[^A-Za-z0-9_-]", "") + "-" + company.replaceAll("[^A-Za-z0-9]+", "-") + ".pdf";
                fileId = files.storeBytes(ctx.collegeId(), ctx.userId(), pdf, fileName, "OFF_CAMPUS_NOC").getId().toString();
                generated = true;
            }
        }
        String offerId = placement ? recordPlacement(ctx, n, ctc, id) : null;
        jdbc.update("""
                update off_campus_noc_requests set status = ?, decision_note = ?, noc_file_id = ?, noc_generated = ?, college_offer_id = ?, decided_by = ?,
                    decided_by_name = ?, decided_at = now() where id = ?""", decision.name(), cleanNote, fileId, generated, offerId, ctx.userId().toString(),
                ctx.actorName(), id.toString());
        notifications.notify(UUID.fromString((String) n.get("user_id")), "OFF_CAMPUS", decision == NocStatus.APPROVED
                ? "Your placement cell issued the NOC for the " + company + " offer. Download it from Off-campus jobs → My applications."
                : "Your placement cell did not issue a NOC for the " + company + " offer: " + cleanNote);
        audit.log(ctx, "OFF_CAMPUS_NOC_" + decision.name(), "OFF_CAMPUS_NOC", id,
                (decision == NocStatus.APPROVED ? "Issued NOC to " : "Refused NOC to ") + student + " for " + company + " · " + role
                        + (generated ? " (generated)" : decision == NocStatus.APPROVED ? " (signed copy uploaded)" : ""), cleanNote);
        return forCollege(ctx, null).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow(OffCampusNocService::notFound);
    }

    /**
     * The off-campus offer as an ACCEPTED college offer: company found by name (or added to the college's companies), offer letter =
     * the one the student attached. Placement policy notes are logged, not enforced: approving the NOC is the college's own decision.
     */
    private String recordPlacement(CollegeContext ctx, Map<String, Object> n, BigDecimal ctc, UUID nocId) {
        UUID collegeId = ctx.collegeId();
        CollegeStudent s = directory.student(collegeId, UUID.fromString(n.get("student_id").toString()));
        String companyName = ((String) n.get("company_name")).trim();
        CollegeCompany company = companies.findAllByCollegeIdAndDeletedAtIsNullOrderByNameAsc(collegeId).stream()
                .filter(c -> c.getName().equalsIgnoreCase(companyName)).findFirst()
                .orElseGet(() -> companies.save(CollegeCompany.builder().collegeId(collegeId).name(companyName.length() > 200 ? companyName.substring(0, 200) : companyName).build()));
        List<CollegeTier> tiers = directory.tiers(collegeId);
        List<String> notes = EligibilityEngine.policyViolations(CollegeDrive.builder().ctcLpa(ctc).build(), directory.profile(collegeId), tiers,
                offers.findAllByCollegeIdAndStudentId(collegeId, s.getId()));
        CollegeTier tier = EligibilityEngine.tierFor(ctc, tiers);
        String ref = "NOC/" + LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear() + "/" + nocId.toString().substring(0, 8).toUpperCase();
        Object letter = n.get("offer_file_id");
        CollegeOffer o = offers.save(CollegeOffer.builder().collegeId(collegeId).studentId(s.getId()).companyId(company.getId())
                .roleTitle((String) n.get("role_title")).ctcLpa(ctc).tierRank(tier == null ? null : tier.getTierRank()).offerDate(LocalDate.now())
                .status(OfferStatus.ACCEPTED).offerLetterFileId(letter == null ? null : UUID.fromString(letter.toString()))
                .remarks("Off-campus offer, recorded with " + ref).build());
        jdbc.update("update off_campus_applications set offer_ctc_lpa = ? where id = ?", ctc, n.get("application_id").toString());
        studentService.recomputePlacement(collegeId, s);
        audit.log(ctx, "OFFER_CREATED", "OFFER", o.getId(), "Off-campus offer " + companyName + " " + ctc.stripTrailingZeros().toPlainString() + " LPA for "
                + s.getRollNumber() + " recorded from the NOC", notes.isEmpty() ? null : "Placement policy notes: " + String.join("; ", notes));
        return o.getId().toString();
    }

    public CollegeFile collegeFile(CollegeContext ctx, UUID id, String kind) {
        return fileOf(row(ctx, id), kind);
    }

    // ================================================================== helpers

    private byte[] generate(CollegeContext ctx, UUID id, Map<String, Object> n, String remarks) {
        Map<String, Object> c = jdbc.queryForMap("""
                select p.college_name, p.university, p.city, p.state, p.tpo_name, p.tpo_email, p.logo_file_id, s.degree
                from college_profiles p join college_students s on s.college_id = p.college_id where p.college_id = ? and s.id = ?""",
                ctx.collegeId().toString(), n.get("student_id"));
        byte[] logo = null;
        if (c.get("logo_file_id") != null) {
            try (InputStream in = files.load(files.get(ctx.collegeId(), UUID.fromString(c.get("logo_file_id").toString()))).getInputStream()) {
                logo = in.readAllBytes();
            } catch (Exception e) {
                log.info("[NOC] college logo not used: {}", e.getMessage());
            }
        }
        String line = String.join(", ", java.util.stream.Stream.of(c.get("university"), c.get("city"), c.get("state"))
                .filter(o -> o != null && !o.toString().isBlank()).map(Object::toString).toList());
        BigDecimal ctc = (BigDecimal) n.get("offer_ctc_lpa");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        String ref = "NOC/" + today.getYear() + "/" + id.toString().substring(0, 8).toUpperCase();
        String signatory = c.get("tpo_name") != null && !c.get("tpo_name").toString().isBlank() ? c.get("tpo_name").toString() : ctx.actorName();
        try {
            return NocLetter.pdf(new NocLetter.Data(ref, today, (String) c.get("college_name"), line, logo, nameOf(n), (String) n.get("roll_number"),
                    (String) c.get("degree"), (String) n.get("branch"), (Integer) n.get("batch_year"), (String) n.get("company_name"),
                    (String) n.get("role_title"), jobTypeLabel((String) n.get("job_type")),
                    ctc == null ? null : ctc.stripTrailingZeros().toPlainString() + " LPA", remarks, signatory, (String) c.get("tpo_email")));
        } catch (IOException e) {
            throw new DomainException("Could not create the NOC PDF", HttpStatus.INTERNAL_SERVER_ERROR, "NOC_PDF_FAILED");
        }
    }

    /** A request of this college that the staff member may see (branch scope). */
    private Map<String, Object> row(CollegeContext ctx, UUID id) {
        StringBuilder where = new StringBuilder(" where n.college_id = ? and n.id = ?");
        List<Object> args = new ArrayList<>(List.of(ctx.collegeId().toString(), id.toString()));
        scope(ctx, where, args);
        return jdbc.queryForList(COLLEGE_SQL + where, args.toArray()).stream().findFirst().orElseThrow(OffCampusNocService::notFound);
    }

    private static void scope(CollegeContext ctx, StringBuilder where, List<Object> args) {
        if (ctx.branchScope().isEmpty()) return;
        where.append(" and s.branch_id in (").append(String.join(",", Collections.nCopies(ctx.branchScope().size(), "?"))).append(")");
        ctx.branchScope().forEach(b -> args.add(b.toString()));
    }

    private CollegeFile fileOf(Map<String, Object> n, String kind) {
        Object fid = "noc".equals(kind) ? n.get("noc_file_id") : n.get("offer_file_id");
        if (fid == null) throw new DomainException("The NOC is not issued yet", HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
        return files.get(UUID.fromString(n.get("college_id").toString()), UUID.fromString(fid.toString()));
    }

    private Noc studentNoc(UUID id) {
        return jdbc.query("select * from off_campus_noc_requests where id = ?", (rs, i) -> new Noc(UUID.fromString(rs.getString("id")),
                NocStatus.valueOf(rs.getString("status")), rs.getString("student_note"), rs.getString("decision_note"), rs.getString("noc_file_id") != null,
                rs.getBoolean("noc_generated"), rs.getString("decided_by_name"), instant(rs.getTimestamp("decided_at")),
                instant(rs.getTimestamp("created_at"))), id.toString()).get(0);
    }

    private static String nameOf(Map<String, Object> n) {
        Object last = n.get("last_name");
        return n.get("first_name") + (last == null || last.toString().isBlank() ? "" : " " + last);
    }

    private static String jobTypeLabel(String t) {
        return switch (t) {
            case "INTERNSHIP" -> "Internship";
            case "INTERNSHIP_PPO" -> "Internship with pre-placement offer";
            default -> "Full-time";
        };
    }

    private static DomainException notFound() {
        return new DomainException("NOC request not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
