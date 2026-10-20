package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
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
    private final MemberRepository memberRepository;

    @Transactional
    public Sanction execute(Long memberId, LocalDate sanctionDate, BigDecimal amount,
                            String reason, SanctionOrigin origin, Long referenceId) {
        // Une sanction disciplinaire (manuelle) ne vise qu'un membre actif
        // (Member#canReceiveSanction). Les sanctions automatiques, elles, naissent
        // d'une obligation antérieure (échec de cotisation…) et restent dues.
        if (origin == null || origin == SanctionOrigin.manual) {
            Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessRuleException("Membre introuvable : " + memberId + "."));
            if (!member.canReceiveSanction()) {
                throw new BusinessRuleException(
                    "Sanction refusée : " + member.getFullName() + " n'est pas un membre actif ("
                  + member.getStatusLabel() + ").");
            }
        }

        Sanction sanction = new Sanction(
            null, memberId, sanctionDate, amount, reason,
            origin != null ? origin : SanctionOrigin.manual,
            referenceId, SanctionStatus.unpaid, null, null,
            LocalDateTime.now(), LocalDateTime.now()
        );
        return sanctionRepository.save(sanction);
    }
}
