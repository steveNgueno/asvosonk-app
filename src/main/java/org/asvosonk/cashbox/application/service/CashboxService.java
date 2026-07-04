package org.asvosonk.cashbox.application.service;

import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.domain.model.Cashbox;
import org.asvosonk.cashbox.domain.model.CashboxMovement;
import org.asvosonk.cashbox.domain.repository.CashboxRepository;
import org.asvosonk.cashbox.domain.repository.CashboxMovementRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxEntity;
import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxMovementEntity;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * ALL cashbox writes MUST go through this service.
 * Never update Cashbox balance directly from other services.
 */
@Service
@RequiredArgsConstructor
public class CashboxService {

    private final CashboxRepository         cashboxRepository;
    private final CashboxMovementRepository movementRepository;
    private final EntityManager             entityManager;

    private AppUserEntity toEntity(AppUser domain) {
        return domain != null ? entityManager.getReference(AppUserEntity.class, domain.getId()) : null;
    }

    private MemberEntity toMemberEntity(org.asvosonk.member.domain.model.Member domain) {
        return domain != null ? entityManager.getReference(MemberEntity.class, domain.getId()) : null;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CashboxMovement record(CashboxType type,
                                  MovementDirection direction,
                                  BigDecimal amount,
                                  String reason,
                                  MovementOrigin origin,
                                  MeetingSessionEntity session,
                                  MemberEntity member,
                                  Long referenceId,
                                  AppUser createdBy) {

        if (amount.compareTo(BigDecimal.ZERO) <= 0) return null;

        Cashbox cashbox = cashboxRepository.findByType(type)
            .orElseThrow(() -> new IllegalStateException("Cashbox not found: " + type));

        // Update balance via domain model - we need to create an updated domain model
        // Since Cashbox domain model is immutable, we save the updated state through entity
        CashboxEntity cashboxEntity = entityManager.getReference(CashboxEntity.class, cashbox.getId());
        if (direction == MovementDirection.in) {
            cashboxEntity.setBalance(cashboxEntity.getBalance().add(amount));
        } else {
            cashboxEntity.setBalance(cashboxEntity.getBalance().subtract(amount));
        }
        entityManager.merge(cashboxEntity);

        // Record movement via entity (CashboxMovement domain model is read-only)
        CashboxMovementEntity movement = new CashboxMovementEntity();
        movement.setCashbox(cashboxEntity);
        movement.setDirection(direction);
        movement.setAmount(amount);
        movement.setReason(reason);
        movement.setOrigin(origin);
        movement.setSession(session);
        movement.setMember(member);
        movement.setReferenceId(referenceId);
        movement.setCreatedBy(toEntity(createdBy));

        entityManager.persist(movement);
        return new CashboxMovement(
            movement.getId(), cashbox, movement.getMovementDate(),
            direction, amount, reason, origin,
            member != null ? member.getId() : null,
            session != null ? session.getId() : null,
            referenceId, createdBy != null ? createdBy.getId() : null,
            movement.getCreatedAt()
        );
    }

    // Convenience shortcuts
    @Transactional(propagation = Propagation.MANDATORY)
    public void credit(CashboxType type, BigDecimal amount, String reason,
                       MovementOrigin origin, MeetingSessionEntity session,
                       MemberEntity member, Long refId, AppUser user) {
        record(type, MovementDirection.in, amount, reason, origin, session, member, refId, user);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void debit(CashboxType type, BigDecimal amount, String reason,
                      MovementOrigin origin, MeetingSessionEntity session,
                      MemberEntity member, Long refId, AppUser user) {
        record(type, MovementDirection.out, amount, reason, origin, session, member, refId, user);
    }
}
