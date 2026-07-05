package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use case: list sanctions with optional filtering by member or status.
 */
@Service
@RequiredArgsConstructor
public class ListSanctionsUseCase {

    private final SanctionRepository sanctionRepository;

    @Transactional(readOnly = true)
    public List<Sanction> findByMember(Long memberId) {
        return sanctionRepository.findByMemberIdOrderBySanctionDateDesc(memberId);
    }

    @Transactional(readOnly = true)
    public List<Sanction> findByStatus(SanctionStatus status) {
        return sanctionRepository.findByStatusOrderBySanctionDateDesc(status);
    }

    @Transactional(readOnly = true)
    public List<Sanction> findAll() {
        return sanctionRepository.findAll();
    }
}
