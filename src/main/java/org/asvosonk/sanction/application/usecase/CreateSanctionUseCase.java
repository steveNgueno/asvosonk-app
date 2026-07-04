package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Use case: create a new sanction for a member.
 */
@Service
@RequiredArgsConstructor
public class CreateSanctionUseCase {

    private final EntityManager entityManager;

    @Transactional
    public SanctionEntity execute(Long memberId, LocalDate sanctionDate, BigDecimal amount,
                                  String reason, SanctionOrigin origin, Long referenceId) {
        SanctionEntity sanction = new SanctionEntity();
        sanction.setMember(entityManager.getReference(MemberEntity.class, memberId));
        sanction.setSanctionDate(sanctionDate);
        sanction.setAmount(amount);
        sanction.setReason(reason);
        sanction.setOrigin(origin != null ? origin : SanctionOrigin.manual);
        sanction.setReferenceId(referenceId);
        entityManager.persist(sanction);
        return sanction;
    }
}
