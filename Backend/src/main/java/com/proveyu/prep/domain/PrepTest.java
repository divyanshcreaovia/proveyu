package com.proveyu.prep.domain;

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
    private String title;
    private String description;
    @Column(name = "company_tag")
    private String companyTag;
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
