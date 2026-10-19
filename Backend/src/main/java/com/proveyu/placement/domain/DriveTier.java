package com.proveyu.placement.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "drive_tier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveTier {

    @EmbeddedId
    private DriveTierId id;
}
