package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Use case: create a new sanction for a member.
 */
@Service
@RequiredArgsConstructor
public class CreateSanctionUseCase {

    private final SanctionRepository sanctionRepository;

    @Transactional
    public Sanction execute(Long memberId, LocalDate sanctionDate, BigDecimal amount,
                            String reason, SanctionOrigin origin, Long referenceId) {
        Sanction sanction = new Sanction(
            null, memberId, sanctionDate, amount, reason,
            origin != null ? origin : SanctionOrigin.manual,
            referenceId, SanctionStatus.unpaid, null,
            LocalDateTime.now(), LocalDateTime.now()
        );
        return sanctionRepository.save(sanction);
    }
}
