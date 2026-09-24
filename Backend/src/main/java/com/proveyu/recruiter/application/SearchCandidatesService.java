package com.proveyu.recruiter.application;

import com.proveyu.recruiter.domain.CandidateSearchIndex;
import com.proveyu.recruiter.infrastructure.CandidateSearchIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchCandidatesService {

    private final CandidateSearchIndexRepository searchIndexRepository;

    @Transactional(readOnly = true)
    public Page<CandidateSearchIndex> searchCandidates(UUID skillId, Pageable pageable) {
        if (skillId != null) {
            return searchIndexRepository.findAll(
                    (root, query, cb) -> cb.equal(root.get("skillId"), skillId),
                    pageable
            );
        }
        return searchIndexRepository.findAll(pageable);
    }
}
