package org.asvosonk.aid.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.aid.domain.model.Aid;
import org.asvosonk.aid.domain.repository.AidRepository;
import org.asvosonk.aid.domain.valueobject.AidStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Consultation des aides : liste complète, filtres par statut, membre ou type. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListAidsUseCase {

    private final AidRepository aidRepository;

    public List<Aid> findAll() {
        return aidRepository.findAll();
    }

    public List<Aid> findByStatus(AidStatus status) {
        return aidRepository.findByStatus(status);
    }

    /** Filtre combiné membre + statut (les paramètres null sont ignorés). */
    public List<Aid> search(Long memberId, AidStatus status) {
        if (memberId != null && status != null) {
            return aidRepository.findByBeneficiaryIdAndStatus(memberId, status).stream()
                .toList();
        }
        if (memberId != null) {
            return aidRepository.findByBeneficiaryId(memberId);
        }
        if (status != null) {
            return aidRepository.findByStatus(status);
        }
        return aidRepository.findAll();
    }

    /** Aides encore d'actualité — proposées aux recouvrements. */
    public List<Aid> findCurrentAids() {
        return aidRepository.findCurrentAids();
    }
}
