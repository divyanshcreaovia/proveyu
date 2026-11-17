package com.proveyu.interview.infrastructure;

import com.proveyu.interview.domain.InterviewCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InterviewCreditRepository extends JpaRepository<InterviewCredit, UUID> {
}
