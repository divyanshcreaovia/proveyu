package com.proveyu.placement.infrastructure;

import com.proveyu.shared.error.DomainException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class ExcelStudentParser {

    private static final Logger log = LoggerFactory.getLogger(ExcelStudentParser.class);

    public static class ParsedStudentRow {
        public int rowNumber;
        public String rollNumber;
        public String firstName;
        public String lastName;
        public String email;
        public String phone;
        public String dob;
        public String gender;
        public String aadharNumber;
        public String panNumber;
        public String residentialAddress;
        public String collegeName;
        public String batchYear;
        public String branch;
        public String degree;
        public Double tenthPercentage;
        public Double twelfthPercentage;
        public int activeBacklogs = 0;
        public int gapYears = 0;
        public String gradDate;
        public Double sem1Sgpa;
        public Double sem2Sgpa;
        public Double sem3Sgpa;
        public Double sem4Sgpa;
        public Double sem5Sgpa;
        public Double sem6Sgpa;
        public Double sem7Sgpa;
        public Double sem8Sgpa;
        public Double aggregateCgpa;
        public String placementStatus = "Unplaced";
        public String placedCompany;
        public String placedRole;
        public String placedCtc;
        public String linkedinUrl;
        public String githubUrl;
        public String portfolioUrl;
        public List<String> errors = new ArrayList<>();

        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    public List<ParsedStudentRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("Uploaded file is empty", HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        try {
            if (originalFilename.endsWith(".csv")) {
                return parseCsv(file.getInputStream());
            } else if (originalFilename.endsWith(".xlsx") || originalFilename.endsWith(".xls")) {
                return parseExcel(file.getInputStream());
            } else {
                // Try excel first, if fails try csv
                try {
                    return parseExcel(file.getInputStream());
                } catch (Exception ex) {
                    return parseCsv(file.getInputStream());
                }
            }
        } catch (DomainException de) {
            throw de;
        } catch (Exception e) {
            log.error("Failed to parse file: {}", e.getMessage(), e);
            throw new DomainException("Failed to parse file: " + e.getMessage(), HttpStatus.BAD_REQUEST, "PARSE_ERROR");
        }
    }

    private List<ParsedStudentRow> parseExcel(InputStream is) throws Exception {
        List<ParsedStudentRow> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                return rows;
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return rows;
            }

            Map<String, Integer> headerMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String val = getCellValueAsString(cell).trim().toLowerCase();
                headerMap.put(val, cell.getColumnIndex());
            }

            int lastRowNum = sheet.getLastRowNum();
            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                ParsedStudentRow student = new ParsedStudentRow();
                student.rowNumber = r + 1;

                student.rollNumber = getMappedValue(row, headerMap, "roll number", "roll", "roll_number", "rollno");
                student.firstName = getMappedValue(row, headerMap, "first name", "firstname", "first_name");
                student.lastName = getMappedValue(row, headerMap, "last name", "lastname", "last_name");
                student.email = getMappedValue(row, headerMap, "college email", "email", "student email");
                student.phone = getMappedValue(row, headerMap, "mobile phone", "phone", "mobile", "contact");
                student.dob = getMappedValue(row, headerMap, "date of birth", "dob", "birth date");
                student.gender = getMappedValue(row, headerMap, "gender", "sex");
                student.aadharNumber = getMappedValue(row, headerMap, "aadhaar number", "aadhar", "aadhaar");
                student.panNumber = getMappedValue(row, headerMap, "pan number", "pan");
                student.residentialAddress = getMappedValue(row, headerMap, "residential address", "address");
                student.collegeName = getMappedValue(row, headerMap, "college name", "college");
                student.batchYear = getMappedValue(row, headerMap, "batch passing year", "batch year", "batch", "passing year");
                student.branch = getMappedValue(row, headerMap, "branch department", "branch", "department", "stream");
                student.degree = getMappedValue(row, headerMap, "degree name", "degree", "course");

                student.tenthPercentage = parseDouble(getMappedValue(row, headerMap, "10th marks (%)", "10th percentage", "10th", "tenth"));
                student.twelfthPercentage = parseDouble(getMappedValue(row, headerMap, "12th marks (%)", "12th percentage", "12th", "twelfth"));
                student.activeBacklogs = parseInt(getMappedValue(row, headerMap, "active backlogs", "backlogs"), 0);
                student.gapYears = parseInt(getMappedValue(row, headerMap, "education gap years", "gap years", "gaps"), 0);
                student.gradDate = getMappedValue(row, headerMap, "graduation date", "grad date");

                student.sem1Sgpa = parseDouble(getMappedValue(row, headerMap, "sem 1 sgpa", "sem1", "sem 1"));
                student.sem2Sgpa = parseDouble(getMappedValue(row, headerMap, "sem 2 sgpa", "sem2", "sem 2"));
                student.sem3Sgpa = parseDouble(getMappedValue(row, headerMap, "sem 3 sgpa", "sem3", "sem 3"));
                student.sem4Sgpa = parseDouble(getMappedValue(row, headerMap, "sem 4 sgpa", "sem4", "sem 4"));
                student.sem5Sgpa = parseDouble(getMappedValue(row, headerMap, "sem 5 sgpa", "sem5", "sem 5"));
                student.sem6Sgpa = parseDouble(getMappedValue(row, headerMap, "sem 6 sgpa", "sem6", "sem 6"));
                student.sem7Sgpa = parseDouble(getMappedValue(row, headerMap, "sem 7 sgpa", "sem7", "sem 7"));
                student.sem8Sgpa = parseDouble(getMappedValue(row, headerMap, "sem 8 sgpa", "sem8", "sem 8"));
                student.aggregateCgpa = parseDouble(getMappedValue(row, headerMap, "aggregate cgpa", "cgpa"));

                student.placementStatus = getMappedValue(row, headerMap, "placement status", "status");
                if (student.placementStatus == null || student.placementStatus.trim().isEmpty()) {
                    student.placementStatus = "Unplaced";
                }
                student.placedCompany = getMappedValue(row, headerMap, "placed company", "company");
                student.placedRole = getMappedValue(row, headerMap, "placed role", "role", "designation");
                student.placedCtc = getMappedValue(row, headerMap, "placed ctc", "ctc", "package");

                student.linkedinUrl = getMappedValue(row, headerMap, "linkedin profile", "linkedin", "linkedin url");
                student.githubUrl = getMappedValue(row, headerMap, "github profile", "github", "github url");
                student.portfolioUrl = getMappedValue(row, headerMap, "portfolio url", "portfolio");

                validateRow(student);
                rows.add(student);
            }
        }
        return rows;
    }

    private List<ParsedStudentRow> parseCsv(InputStream is) throws Exception {
        List<ParsedStudentRow> rows = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        
        // Handle BOM if present
        reader.mark(1);
        if (reader.read() != 0xFEFF) {
            reader.reset();
        }

        CSVParser csvParser = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .setSkipHeaderRecord(true)
                .build()
                .parse(reader);

        int rowNum = 1;
        for (CSVRecord record : csvParser) {
            rowNum++;
            if (isRecordEmpty(record)) continue;

            ParsedStudentRow student = new ParsedStudentRow();
            student.rowNumber = rowNum;

            student.rollNumber = getCsvValue(record, "Roll Number", "roll", "roll_number", "rollno");
            student.firstName = getCsvValue(record, "First Name", "firstname", "first_name");
            student.lastName = getCsvValue(record, "Last Name", "lastname", "last_name");
            student.email = getCsvValue(record, "College Email", "email", "student email");
            student.phone = getCsvValue(record, "Mobile Phone", "phone", "mobile", "contact");
            student.dob = getCsvValue(record, "Date of Birth", "dob", "birth date");
            student.gender = getCsvValue(record, "Gender", "sex");
            student.aadharNumber = getCsvValue(record, "Aadhaar Number", "aadhar", "aadhaar");
            student.panNumber = getCsvValue(record, "PAN Number", "pan");
            student.residentialAddress = getCsvValue(record, "Residential Address", "address");
            student.collegeName = getCsvValue(record, "College Name", "college");
            student.batchYear = getCsvValue(record, "Batch Passing Year", "batch year", "batch", "passing year");
            student.branch = getCsvValue(record, "Branch Department", "branch", "department", "stream");
            student.degree = getCsvValue(record, "Degree Name", "degree", "course");

            student.tenthPercentage = parseDouble(getCsvValue(record, "10th Marks (%)", "10th percentage", "10th"));
            student.twelfthPercentage = parseDouble(getCsvValue(record, "12th Marks (%)", "12th percentage", "12th"));
            student.activeBacklogs = parseInt(getCsvValue(record, "Active Backlogs", "backlogs"), 0);
            student.gapYears = parseInt(getCsvValue(record, "Education Gap Years", "gap years"), 0);
            student.gradDate = getCsvValue(record, "Graduation Date", "grad date");

            student.sem1Sgpa = parseDouble(getCsvValue(record, "Sem 1 SGPA", "sem1", "sem 1"));
            student.sem2Sgpa = parseDouble(getCsvValue(record, "Sem 2 SGPA", "sem2", "sem 2"));
            student.sem3Sgpa = parseDouble(getCsvValue(record, "Sem 3 SGPA", "sem3", "sem 3"));
            student.sem4Sgpa = parseDouble(getCsvValue(record, "Sem 4 SGPA", "sem4", "sem 4"));
            student.sem5Sgpa = parseDouble(getCsvValue(record, "Sem 5 SGPA", "sem5", "sem 5"));
            student.sem6Sgpa = parseDouble(getCsvValue(record, "Sem 6 SGPA", "sem6", "sem 6"));
            student.sem7Sgpa = parseDouble(getCsvValue(record, "Sem 7 SGPA", "sem7", "sem 7"));
            student.sem8Sgpa = parseDouble(getCsvValue(record, "Sem 8 SGPA", "sem8", "sem 8"));
            student.aggregateCgpa = parseDouble(getCsvValue(record, "Aggregate CGPA", "cgpa"));

            student.placementStatus = getCsvValue(record, "Placement Status", "status");
            if (student.placementStatus == null || student.placementStatus.trim().isEmpty()) {
                student.placementStatus = "Unplaced";
            }
            student.placedCompany = getCsvValue(record, "Placed Company", "company");
            student.placedRole = getCsvValue(record, "Placed Role", "role");
            student.placedCtc = getCsvValue(record, "Placed CTC", "ctc");

            student.linkedinUrl = getCsvValue(record, "LinkedIn Profile", "linkedin");
            student.githubUrl = getCsvValue(record, "GitHub Profile", "github");
            student.portfolioUrl = getCsvValue(record, "Portfolio URL", "portfolio");

            validateRow(student);
            rows.add(student);
        }

        return rows;
    }

    private void validateRow(ParsedStudentRow row) {
        if (row.rollNumber == null || row.rollNumber.trim().isEmpty()) {
            row.errors.add("Roll Number is required");
        }
        if (row.email == null || row.email.trim().isEmpty() || !row.email.contains("@")) {
            row.errors.add("Valid College Email is required");
        }
        if (row.firstName == null || row.firstName.trim().isEmpty()) {
            row.errors.add("First Name is required");
        }
        if (row.branch == null || row.branch.trim().isEmpty()) {
            row.branch = "Computer Science & Engineering"; // sensible default if blank
        }
        if (row.batchYear == null || row.batchYear.trim().isEmpty()) {
            row.batchYear = "2024";
        }
        if (row.degree == null || row.degree.trim().isEmpty()) {
            row.degree = "B.Tech";
        }
    }

    private String getMappedValue(Row row, Map<String, Integer> headerMap, String... possibleKeys) {
        for (String key : possibleKeys) {
            Integer colIdx = headerMap.get(key.toLowerCase());
            if (colIdx != null) {
                Cell cell = row.getCell(colIdx);
                String val = getCellValueAsString(cell);
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        }
        return null;
    }

    private String getCsvValue(CSVRecord record, String... possibleKeys) {
        for (String key : possibleKeys) {
            try {
                if (record.isMapped(key)) {
                    String val = record.get(key);
                    if (val != null && !val.trim().isEmpty()) {
                        return val.trim();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    yield String.valueOf((long) num);
                }
                yield String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        yield cell.getStringCellValue();
                    } catch (Exception ex) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellValueAsString(cell);
                if (val != null && !val.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isRecordEmpty(CSVRecord record) {
        for (String val : record) {
            if (val != null && !val.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Double parseDouble(String str) {
        if (str == null || str.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(str.trim().replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private int parseInt(String str, int defaultVal) {
        if (str == null || str.trim().isEmpty()) return defaultVal;
        try {
            return Integer.parseInt(str.trim().replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
