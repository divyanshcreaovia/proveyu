package com.proveyu.college.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.college.domain.CollegeBranch;
import com.proveyu.college.domain.CollegeStudent;
import com.proveyu.college.domain.ImportJob;
import com.proveyu.college.infrastructure.CollegeStudentRepository;
import com.proveyu.college.infrastructure.ImportJobRepository;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Bulk student import from XLSX / CSV.
 * <ul>
 *   <li>Dry run validates every row and returns errors + a preview without writing anything (except the import-history row).</li>
 *   <li>Duplicate detection: repeated roll number / e-mail / phone inside the file, and e-mail or phone already used by a
 *       different student in the database. Rows whose roll number already exists are UPDATEs.</li>
 *   <li>Commit applies only valid, non-duplicate rows.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StudentImportService {

    public static final List<String> COLUMNS = List.of("roll_number", "first_name", "last_name", "email", "phone", "gender", "dob",
            "branch_code", "batch_year", "degree", "cgpa", "tenth_percent", "twelfth_percent", "active_backlogs", "gap_years",
            "skills", "linkedin_url", "github_url");
    private static final Set<String> REQUIRED = Set.of("roll_number", "first_name", "email", "branch_code", "batch_year");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+\\- ]{7,20}$");
    private static final int MAX_ROWS = 5000;

    private final CollegeStudentRepository studentRepository;
    private final ImportJobRepository importJobRepository;
    private final CollegeDirectory directory;
    private final CollegeFileService fileService;
    private final StudentAccountService accountService;
    private final StudentService studentService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public record RowIssue(int row, String field, String message) {}

    public record PreviewRow(int row, String action, String rollNumber, String name, String email, String branchCode, Integer batchYear, BigDecimal cgpa) {}

    public record ImportResult(UUID jobId, boolean dryRun, int totalRows, int validRows, int created, int updated, int errorRows,
                               int duplicateRows, List<RowIssue> errors, List<RowIssue> duplicates, List<PreviewRow> preview) {}

    public record ImportHistory(UUID id, String fileName, boolean dryRun, int totalRows, int createdRows, int updatedRows, int errorRows,
                                int duplicateRows, Instant createdAt) {}

    private record Parsed(int row, Map<String, String> v) {}

    private record Valid(int row, Map<String, String> v, CollegeBranch branch, CollegeStudent existing) {}

    @Transactional
    public ImportResult importFile(CollegeContext ctx, MultipartFile file, boolean dryRun) {
        fileService.validate(file, CollegeFileService.SHEET_EXT);
        List<Parsed> rows = parse(file);
        if (rows.isEmpty()) throw new DomainException("The file has no data rows", HttpStatus.BAD_REQUEST, "IMPORT_EMPTY");
        if (rows.size() > MAX_ROWS) throw new DomainException("Maximum " + MAX_ROWS + " rows per import", HttpStatus.BAD_REQUEST, "IMPORT_TOO_LARGE");

        Map<String, CollegeBranch> branchesByCode = new HashMap<>();
        directory.branches(ctx.collegeId()).values().forEach(b -> branchesByCode.put(b.getCode().toLowerCase(Locale.ROOT), b));

        List<RowIssue> errors = new ArrayList<>();
        List<RowIssue> duplicates = new ArrayList<>();
        List<Valid> valid = new ArrayList<>();
        Map<String, Integer> seenRoll = new HashMap<>(), seenEmail = new HashMap<>(), seenPhone = new HashMap<>();
        Set<Integer> errorRowNumbers = new HashSet<>();

        for (Parsed p : rows) {
            Map<String, String> v = p.v();
            List<RowIssue> rowErrors = validateRow(p.row(), v);
            CollegeBranch branch = null;
            String code = v.get("branch_code");
            if (code != null) {
                branch = branchesByCode.get(code.toLowerCase(Locale.ROOT));
                if (branch == null) rowErrors.add(new RowIssue(p.row(), "branch_code", "Unknown branch code '" + code + "'"));
                else if (!ctx.canAccessBranch(branch.getId())) rowErrors.add(new RowIssue(p.row(), "branch_code", "Branch outside your scope"));
            }
            if (!rowErrors.isEmpty()) {
                errors.addAll(rowErrors);
                errorRowNumbers.add(p.row());
                continue;
            }
            String roll = v.get("roll_number").toLowerCase(Locale.ROOT);
            String email = v.get("email").toLowerCase(Locale.ROOT);
            String phone = v.get("phone");
            Integer dupOf = seenRoll.get(roll);
            if (dupOf == null) dupOf = seenEmail.get(email);
            if (dupOf == null && phone != null) dupOf = seenPhone.get(phone);
            if (dupOf != null) {
                duplicates.add(new RowIssue(p.row(), "row", "Duplicate of row " + dupOf + " in this file (roll / e-mail / phone)"));
                continue;
            }
            seenRoll.put(roll, p.row());
            seenEmail.put(email, p.row());
            if (phone != null) seenPhone.put(phone, p.row());

            CollegeStudent existing = studentRepository.findByCollegeIdAndRollNumberIgnoreCase(ctx.collegeId(), v.get("roll_number")).orElse(null);
            var emailOwner = studentRepository.findByCollegeIdAndEmailIgnoreCase(ctx.collegeId(), email);
            if (emailOwner.isPresent() && (existing == null || !emailOwner.get().getId().equals(existing.getId()))) {
                duplicates.add(new RowIssue(p.row(), "email", "E-mail already used by student " + emailOwner.get().getRollNumber()));
                continue;
            }
            if (phone != null) {
                var phoneOwners = studentRepository.findAllByCollegeIdAndPhone(ctx.collegeId(), phone).stream()
                        .filter(o -> existing == null || !o.getId().equals(existing.getId())).toList();
                if (!phoneOwners.isEmpty()) {
                    duplicates.add(new RowIssue(p.row(), "phone", "Phone already used by student " + phoneOwners.get(0).getRollNumber()));
                    continue;
                }
            }
            if (existing != null && !ctx.canAccessBranch(existing.getBranchId())) {
                errors.add(new RowIssue(p.row(), "roll_number", "Existing student belongs to a branch outside your scope"));
                errorRowNumbers.add(p.row());
                continue;
            }
            valid.add(new Valid(p.row(), v, branch, existing));
        }

        int created = 0, updated = 0;
        if (!dryRun) {
            for (Valid row : valid) {
                if (row.existing() == null) {
                    CollegeStudent s = CollegeStudent.builder().collegeId(ctx.collegeId()).build();
                    fill(s, row.v(), row.branch());
                    var account = accountService.ensureCandidate(s.getEmail(), CollegeDirectory.fullName(s), s.getPhone());
                    s.setUserId(account.userId());
                    s = studentRepository.save(s);
                    studentService.sendWelcome(ctx.collegeId(), s);
                    created++;
                } else {
                    CollegeStudent s = row.existing();
                    boolean emailChanged = !s.getEmail().equalsIgnoreCase(row.v().get("email"));
                    fill(s, row.v(), row.branch());
                    if (emailChanged) s.setUserId(accountService.ensureCandidate(s.getEmail(), CollegeDirectory.fullName(s), s.getPhone()).userId());
                    s.setDeletedAt(null);
                    studentRepository.save(s);
                    updated++;
                }
            }
        }

        List<PreviewRow> preview = valid.stream().limit(100).map(r -> new PreviewRow(r.row(), r.existing() == null ? "CREATE" : "UPDATE",
                r.v().get("roll_number"), (r.v().get("first_name") + " " + Objects.toString(r.v().get("last_name"), "")).trim(),
                r.v().get("email"), r.branch().getCode(), Integer.valueOf(r.v().get("batch_year")), dec(r.v().get("cgpa")))).toList();

        ImportJob job = importJobRepository.save(ImportJob.builder().collegeId(ctx.collegeId()).createdBy(ctx.userId())
                .fileName(Objects.toString(file.getOriginalFilename(), "upload")).dryRun(dryRun).totalRows(rows.size())
                .createdRows(created).updatedRows(updated).errorRows(errorRowNumbers.size()).duplicateRows(duplicates.size())
                .errorsJson(json(Map.of("errors", errors, "duplicates", duplicates))).build());
        if (!dryRun) {
            auditService.log(ctx, "STUDENTS_IMPORTED", "IMPORT", job.getId(), "Imported " + file.getOriginalFilename() + ": " + created
                    + " created, " + updated + " updated, " + errorRowNumbers.size() + " errors, " + duplicates.size() + " duplicates", null);
        }
        return new ImportResult(job.getId(), dryRun, rows.size(), valid.size(), created, updated, errorRowNumbers.size(), duplicates.size(),
                errors, duplicates, preview);
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportHistory> history(CollegeContext ctx, int page, int size) {
        return PageResponse.of(importJobRepository.findAllByCollegeIdOrderByCreatedAtDesc(ctx.collegeId(), PageRequest.of(page, Math.min(size, 100))),
                j -> new ImportHistory(j.getId(), j.getFileName(), j.isDryRun(), j.getTotalRows(), j.getCreatedRows(), j.getUpdatedRows(),
                        j.getErrorRows(), j.getDuplicateRows(), j.getCreatedAt()));
    }

    public byte[] template(CollegeContext ctx) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("students");
            Row h = sheet.createRow(0);
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            for (int i = 0; i < COLUMNS.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(COLUMNS.get(i));
                c.setCellStyle(bold);
            }
            List<CollegeBranch> branches = new ArrayList<>(directory.branches(ctx.collegeId()).values());
            String code = branches.isEmpty() ? "CSE" : branches.get(0).getCode();
            String[] sample = {"21CS001", "Aarav", "Sharma", "aarav.sharma@example.edu", "9876543210", "Male", "2003-05-14", code,
                    String.valueOf(LocalDate.now().getYear() + 1), "B.Tech", "8.4", "88.5", "91.2", "0", "0", "Java; SQL; React", "", ""};
            Row r = sheet.createRow(1);
            for (int i = 0; i < sample.length; i++) r.createCell(i).setCellValue(sample[i]);
            for (int i = 0; i < COLUMNS.size(); i++) sheet.setColumnWidth(i, 18 * 256);

            Sheet help = wb.createSheet("instructions");
            String[] lines = {"Required columns: roll_number, first_name, email, branch_code, batch_year",
                    "dob format: YYYY-MM-DD   cgpa: 0-10   percentages: 0-100   skills: separated by ; or ,",
                    "Rows with an existing roll_number update that student. Duplicate roll/e-mail/phone rows are skipped.",
                    "", "Valid branch codes:"};
            int i = 0;
            for (String line : lines) help.createRow(i++).createCell(0).setCellValue(line);
            for (CollegeBranch b : branches) {
                Row br = help.createRow(i++);
                br.createCell(0).setCellValue(b.getCode());
                br.createCell(1).setCellValue(b.getName());
            }
            help.setColumnWidth(0, 100 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new DomainException("Could not build template", HttpStatus.INTERNAL_SERVER_ERROR, "TEMPLATE_FAILED");
        }
    }

    // ------------------------------------------------------------------ parsing & validation

    List<Parsed> parse(MultipartFile file) {
        String ext = CollegeFileService.extensionOf(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            return ext.equals(".csv") ? parseCsv(in) : parseExcel(in);
        } catch (IOException | RuntimeException e) {
            if (e instanceof DomainException de) throw de;
            throw new DomainException("Could not read the file: " + e.getMessage(), HttpStatus.BAD_REQUEST, "IMPORT_UNREADABLE");
        }
    }

    private List<Parsed> parseCsv(InputStream in) throws IOException {
        List<Parsed> out = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).build()
                .parse(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            Map<String, String> headerMap = new HashMap<>();
            parser.getHeaderNames().forEach(hn -> headerMap.put(normalizeHeader(hn), hn));
            requireHeaders(headerMap.keySet());
            for (CSVRecord rec : parser) {
                Map<String, String> v = new HashMap<>();
                for (String col : COLUMNS) {
                    String hn = headerMap.get(col);
                    if (hn != null && rec.isMapped(hn) && rec.isSet(hn)) v.put(col, blankToNull(rec.get(hn)));
                }
                if (v.values().stream().anyMatch(Objects::nonNull)) out.add(new Parsed((int) rec.getRecordNumber() + 1, v));
            }
        }
        return out;
    }

    private List<Parsed> parseExcel(InputStream in) throws IOException {
        List<Parsed> out = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) return out;
            Map<String, Integer> idx = new HashMap<>();
            for (Cell c : header) idx.put(normalizeHeader(fmt.formatCellValue(c)), c.getColumnIndex());
            requireHeaders(idx.keySet());
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> v = new HashMap<>();
                for (String col : COLUMNS) {
                    Integer i = idx.get(col);
                    if (i == null) continue;
                    Cell c = row.getCell(i);
                    if (c == null) continue;
                    String value;
                    if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                        value = c.getLocalDateTimeCellValue().toLocalDate().toString();
                    } else {
                        value = fmt.formatCellValue(c);
                    }
                    v.put(col, blankToNull(value));
                }
                if (v.values().stream().anyMatch(Objects::nonNull)) out.add(new Parsed(r + 1, v));
            }
        }
        return out;
    }

    private static void requireHeaders(Set<String> headers) {
        List<String> missing = REQUIRED.stream().filter(h -> !headers.contains(h)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new DomainException("Missing required column(s): " + String.join(", ", missing) + ". Download the template for the expected format.",
                    HttpStatus.BAD_REQUEST, "IMPORT_BAD_HEADER");
        }
    }

    private static String normalizeHeader(String h) {
        return h == null ? "" : h.replace("﻿", "").trim().toLowerCase(Locale.ROOT).replaceAll("[\\s.-]+", "_");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    List<RowIssue> validateRow(int row, Map<String, String> v) {
        List<RowIssue> e = new ArrayList<>();
        for (String req : REQUIRED) if (v.get(req) == null) e.add(new RowIssue(row, req, "Required"));
        if (v.get("email") != null && !EMAIL.matcher(v.get("email")).matches()) e.add(new RowIssue(row, "email", "Invalid e-mail"));
        if (v.get("phone") != null && !PHONE.matcher(v.get("phone")).matches()) e.add(new RowIssue(row, "phone", "Invalid phone"));
        if (v.get("roll_number") != null && v.get("roll_number").length() > 50) e.add(new RowIssue(row, "roll_number", "Max 50 characters"));
        checkInt(e, row, v, "batch_year", 2000, 2100);
        checkInt(e, row, v, "active_backlogs", 0, 50);
        checkInt(e, row, v, "gap_years", 0, 20);
        checkDec(e, row, v, "cgpa", 0, 10);
        checkDec(e, row, v, "tenth_percent", 0, 100);
        checkDec(e, row, v, "twelfth_percent", 0, 100);
        if (v.get("dob") != null) {
            try {
                LocalDate d = LocalDate.parse(v.get("dob"));
                if (!d.isBefore(LocalDate.now())) e.add(new RowIssue(row, "dob", "Must be in the past"));
            } catch (DateTimeParseException ex) {
                e.add(new RowIssue(row, "dob", "Use YYYY-MM-DD"));
            }
        }
        return e;
    }

    private static void checkInt(List<RowIssue> e, int row, Map<String, String> v, String f, int min, int max) {
        if (v.get(f) == null) return;
        try {
            int x = new BigDecimal(v.get(f)).intValueExact();
            if (x < min || x > max) e.add(new RowIssue(row, f, "Must be between " + min + " and " + max));
            else v.put(f, String.valueOf(x));
        } catch (NumberFormatException | ArithmeticException ex) {
            e.add(new RowIssue(row, f, "Must be a whole number"));
        }
    }

    private static void checkDec(List<RowIssue> e, int row, Map<String, String> v, String f, double min, double max) {
        if (v.get(f) == null) return;
        try {
            double x = Double.parseDouble(v.get(f));
            if (x < min || x > max) e.add(new RowIssue(row, f, "Must be between " + (int) min + " and " + (int) max));
        } catch (NumberFormatException ex) {
            e.add(new RowIssue(row, f, "Must be a number"));
        }
    }

    private static BigDecimal dec(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private static Integer integer(String s) {
        return s == null ? null : Integer.valueOf(s);
    }

    private static void fill(CollegeStudent s, Map<String, String> v, CollegeBranch branch) {
        s.setRollNumber(v.get("roll_number"));
        s.setFirstName(v.get("first_name"));
        s.setLastName(v.get("last_name"));
        s.setEmail(v.get("email").toLowerCase(Locale.ROOT));
        s.setPhone(v.get("phone"));
        s.setGender(v.get("gender"));
        s.setDob(v.get("dob") == null ? null : LocalDate.parse(v.get("dob")));
        s.setBranchId(branch.getId());
        s.setBatchYear(integer(v.get("batch_year")));
        s.setDegree(v.get("degree"));
        s.setCgpa(dec(v.get("cgpa")));
        s.setTenthPercent(dec(v.get("tenth_percent")));
        s.setTwelfthPercent(dec(v.get("twelfth_percent")));
        s.setActiveBacklogs(v.get("active_backlogs") == null ? 0 : integer(v.get("active_backlogs")));
        s.setGapYears(v.get("gap_years") == null ? 0 : integer(v.get("gap_years")));
        if (v.get("skills") != null) s.setSkills(String.join(", ", StudentService.splitSkills(v.get("skills"))));
        s.setLinkedinUrl(v.get("linkedin_url"));
        s.setGithubUrl(v.get("github_url"));
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
