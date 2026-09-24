package com.proveyu.assessment.web;

import com.proveyu.assessment.domain.Exam;
import com.proveyu.assessment.infrastructure.ExamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
public class AssessmentController {

    private final ExamRepository examRepository;

    @GetMapping
    public ResponseEntity<List<Exam>> getActiveExams() {
        return ResponseEntity.ok(examRepository.findByActiveTrue());
    }
}
