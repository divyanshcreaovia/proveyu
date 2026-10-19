package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "drive_branch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveBranch {

    @EmbeddedId
    private DriveBranchId id;
}
