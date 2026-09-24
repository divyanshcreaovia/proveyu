package com.proveyu.recruiter.infrastructure;

import com.proveyu.recruiter.domain.CandidateSearchIndex;
import com.proveyu.recruiter.domain.CandidateSearchIndex.CandidateSearchIndexId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CandidateSearchIndexRepository extends JpaRepository<CandidateSearchIndex, CandidateSearchIndexId>, JpaSpecificationExecutor<CandidateSearchIndex> {
    List<CandidateSearchIndex> findBySkillId(UUID skillId);
}
