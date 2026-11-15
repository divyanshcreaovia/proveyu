package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "college_import_jobs")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ImportJob extends TenantEntity {
    @Column(name = "file_name")
    private String fileName;
    @Column(name = "dry_run")
    private boolean dryRun;
    @Column(name = "total_rows")
    private int totalRows;
    @Column(name = "created_rows")
    private int createdRows;
    @Column(name = "updated_rows")
    private int updatedRows;
    @Column(name = "error_rows")
    private int errorRows;
    @Column(name = "duplicate_rows")
    private int duplicateRows;
    @Column(name = "errors_json")
    private String errorsJson;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "created_by", columnDefinition = "CHAR(36)")
    private UUID createdBy;
}
