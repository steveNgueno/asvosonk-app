package org.asvosonk.aid.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.aid.domain.model.Aid;
import org.asvosonk.aid.domain.model.AidContribution;
import org.asvosonk.aid.domain.repository.AidContributionRepository;
import org.asvosonk.aid.domain.repository.AidRepository;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Détail d'une aide : qui a recouvert sa part, qui reste dû, combien il
 * reste à recouvrir au total.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAidDetailsUseCase {

    private final AidRepository             aidRepository;
    private final AidContributionRepository aidContributionRepository;

    public Aid getAid(Long id) {
        return aidRepository.findById(id)
            .orElseThrow(() -> new BusinessRuleException("Aide introuvable : " + id));
    }

    public List<AidContribution> getContributions(Long aidId) {
        return aidContributionRepository.findByAidId(aidId);
    }

    /** Total encore à recouvrir sur cette aide. */
    public BigDecimal remainingTotal(Long aidId) {
        return getContributions(aidId).stream()
            .map(AidContribution::getRemaining)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
