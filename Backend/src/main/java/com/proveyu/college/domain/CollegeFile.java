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
@Table(name = "college_files")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CollegeFile extends TenantEntity {
    @Column(name = "original_name")
    private String originalName;
    @Column(name = "stored_name")
    private String storedName;
    @Column(name = "content_type")
    private String contentType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(name = "category")
    private String category;
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "uploaded_by", columnDefinition = "CHAR(36)")
    private UUID uploadedBy;
}
