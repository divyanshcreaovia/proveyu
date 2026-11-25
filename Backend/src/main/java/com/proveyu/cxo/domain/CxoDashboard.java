package com.proveyu.cxo.domain;

import com.proveyu.college.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** A dashboard a CXO saved. Only the spec is stored; the numbers are recomputed from the database whenever it is opened. */
@Entity
@Table(name = "cxo_dashboards")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CxoDashboard extends TenantEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", columnDefinition = "CHAR(36)", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "prompt")
    private String prompt;

    @Column(name = "spec_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String specJson;

    @Builder.Default
    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;
}
