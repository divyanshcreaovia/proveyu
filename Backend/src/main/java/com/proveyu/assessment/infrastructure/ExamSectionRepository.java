package com.proveyu.assessment.infrastructure;

import com.proveyu.assessment.domain.ExamSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExamSectionRepository extends JpaRepository<ExamSection, UUID> {
    List<ExamSection> findByExamId(UUID examId);
}
