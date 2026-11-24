package com.proveyu.student.prep.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An uploaded mock test. The full definition (answers, hidden tests) lives in {@code definitionJson} and never leaves the server unstripped. */
@Entity
@Table(name = "prep_tests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepTest {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    /** The college whose placement team uploaded it (only its students see it); null = platform test for everyone. */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "college_id", columnDefinition = "CHAR(36)")
    private UUID collegeId;
    private String title;
    private String description;
    @Column(name = "company_tag")
    private String companyTag;
    /** EASY / MEDIUM / HARD; label only. */
    private String level;
    /** Position in the unlock ladder; null = always open. */
    @Column(name = "level_order")
    private Integer levelOrder;
    @Column(name = "duration_minutes")
    private int durationMinutes;
    @Column(name = "question_count")
    private int questionCount;
    @Column(name = "coding_count")
    private int codingCount;
    @Column(name = "total_marks")
    private int totalMarks;
    @Column(name = "definition_json")
    private String definitionJson;
    private boolean published;
    /** PRACTICE (any time, retakes) or EXAM (one attempt in a time window, for chosen branches / batches). College tests only. */
    @Builder.Default
    private String mode = "PRACTICE";
    @Column(name = "starts_at")
    private Instant startsAt;
    @Column(name = "ends_at")
    private Instant endsAt;
    /** CSV of branch ids / batch years the exam is for; null = everyone in the college. */
    @Column(name = "branch_ids")
    private String branchIds;
    @Column(name = "batch_years")
    private String batchYears;
    @Column(name = "shuffle_questions")
    private boolean shuffleQuestions;
    @Column(name = "shuffle_options")
    private boolean shuffleOptions;
    /** Correct answers and explanations on the student's result page. */
    @Builder.Default
    @Column(name = "show_answers")
    private boolean showAnswers = true;
    /** Students can see their score (an exam keeps it hidden until the college releases it). */
    @Builder.Default
    @Column(name = "results_released")
    private boolean resultsReleased = true;

    /** When eligible students were told the exam is live (null = not yet; cleared when the window changes). */
    @Column(name = "live_notified_at")
    private Instant liveNotifiedAt;

    public boolean isExam() { return "EXAM".equals(mode); }
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "created_by", columnDefinition = "CHAR(36)")
    private UUID createdBy;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
