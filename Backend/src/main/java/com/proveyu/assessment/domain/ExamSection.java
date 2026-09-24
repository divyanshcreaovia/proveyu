package com.proveyu.assessment.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "exam_sections")
public class ExamSection {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exam_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID examId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "skill_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID skillId;

    @Column(name = "section_name", nullable = false, length = 100)
    private String sectionName;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "total_marks", nullable = false)
    private int totalMarks;

    @Column(name = "passing_marks", nullable = false)
    private int passingMarks;

    public ExamSection() {}

    public ExamSection(UUID id, UUID examId, UUID skillId, String sectionName, int durationMinutes, int totalMarks, int passingMarks) {
        this.id = id;
        this.examId = examId;
        this.skillId = skillId;
        this.sectionName = sectionName;
        this.durationMinutes = durationMinutes;
        this.totalMarks = totalMarks;
        this.passingMarks = passingMarks;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getExamId() { return examId; }
    public void setExamId(UUID examId) { this.examId = examId; }

    public UUID getSkillId() { return skillId; }
    public void setSkillId(UUID skillId) { this.skillId = skillId; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }

    public int getPassingMarks() { return passingMarks; }
    public void setPassingMarks(int passingMarks) { this.passingMarks = passingMarks; }

    public static ExamSectionBuilder builder() { return new ExamSectionBuilder(); }

    public static class ExamSectionBuilder {
        private UUID id;
        private UUID examId;
        private UUID skillId;
        private String sectionName;
        private int durationMinutes;
        private int totalMarks;
        private int passingMarks;

        public ExamSectionBuilder id(UUID id) { this.id = id; return this; }
        public ExamSectionBuilder examId(UUID examId) { this.examId = examId; return this; }
        public ExamSectionBuilder skillId(UUID skillId) { this.skillId = skillId; return this; }
        public ExamSectionBuilder sectionName(String sectionName) { this.sectionName = sectionName; return this; }
        public ExamSectionBuilder durationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; return this; }
        public ExamSectionBuilder totalMarks(int totalMarks) { this.totalMarks = totalMarks; return this; }
        public ExamSectionBuilder passingMarks(int passingMarks) { this.passingMarks = passingMarks; return this; }

        public ExamSection build() {
            return new ExamSection(id, examId, skillId, sectionName, durationMinutes, totalMarks, passingMarks);
        }
    }
}
