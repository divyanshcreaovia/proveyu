package com.proveyu.prep.infrastructure;

import com.proveyu.prep.domain.PrepTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrepTestRepository extends JpaRepository<PrepTest, UUID> {
    List<PrepTest> findAllByOrderByCreatedAtDesc();

    List<PrepTest> findAllByPublishedTrueOrderByCreatedAtDesc();
}
