package com.proveyu.college.application;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class StudentImportServiceTest {

    StudentImportService svc = new StudentImportService(null, null, null, mock(CollegeFileService.class), null, null, null, null);

    @Test
    void validatesEveryRowField() {
        Map<String, String> v = new HashMap<>(Map.of("roll_number", "R1", "first_name", "A", "email", "bad", "branch_code", "CSE",
                "batch_year", "1990", "cgpa", "11", "phone", "12", "dob", "2099-01-01", "active_backlogs", "1.5"));
        var issues = svc.validateRow(2, v);
        assertThat(issues).extracting(StudentImportService.RowIssue::field)
                .contains("email", "batch_year", "cgpa", "phone", "dob", "active_backlogs");
        assertThat(svc.validateRow(3, new HashMap<>())).extracting(StudentImportService.RowIssue::field)
                .contains("roll_number", "first_name", "email", "branch_code", "batch_year");
    }

    @Test
    void acceptsValidRow() {
        Map<String, String> v = new HashMap<>(Map.of("roll_number", "R1", "first_name", "A", "email", "a@b.in", "branch_code", "CSE",
                "batch_year", "2027", "cgpa", "8.25", "phone", "9876543210", "dob", "2003-05-14"));
        assertThat(svc.validateRow(2, v)).isEmpty();
    }

    @Test
    void parsesCsvWithFlexibleHeadersAndSkipsBlankLines() {
        String csv = "﻿Roll Number,First Name,Email,Branch Code,Batch Year\nR1,Asha,a@x.in,CSE,2027\n\nR2,Bala,b@x.in,ECE,2027\n";
        var rows = svc.parse(new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(rows).hasSize(2);
    }

    @Test
    void rejectsFileMissingRequiredColumns() {
        var f = new MockMultipartFile("file", "s.csv", "text/csv", "roll_number,first_name\nR1,A\n".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> svc.parse(f)).hasMessageContaining("Missing required column");
    }
}
