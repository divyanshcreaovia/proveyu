package com.proveyu.college.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "college_tiers")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CollegeTier extends TenantEntity {
    @Column(name = "name")
    private String name;
    @Column(name = "tier_rank")
    private Integer tierRank;
    @Column(name = "min_ctc_lpa")
    private BigDecimal minCtcLpa;
}
