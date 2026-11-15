package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "college_batches")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CollegeBatch extends TenantEntity {
    @Column(name = "grad_year")
    private Integer gradYear;
    @Column(name = "label")
    private String label;
    @Builder.Default
    @Column(name = "active")
    private boolean active = true;
}
