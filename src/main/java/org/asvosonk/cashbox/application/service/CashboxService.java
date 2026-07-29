package org.asvosonk.cashbox.application.service;

import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.domain.model.Cashbox;
import org.asvosonk.cashbox.domain.model.CashboxMovement;
import org.asvosonk.cashbox.domain.repository.CashboxRepository;
import org.asvosonk.cashbox.domain.repository.CashboxMovementRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.common.domain.exception.BusinessRuleException;
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

        // F-38 : un montant nul ou négatif est une erreur explicite, jamais un no-op silencieux.
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Le montant du mouvement doit être strictement positif.");
        }

        Cashbox cashbox = cashboxRepository.findByType(type)
            .orElseThrow(() -> new IllegalStateException("Cashbox not found: " + type));

        // On charge l'état réel (et non un simple proxy) : nécessaire pour lire le solde
        // courant, appliquer la garde F-04, et laisser @Version détecter un accès concurrent (F-16).
        CashboxEntity cashboxEntity = entityManager.find(CashboxEntity.class, cashbox.getId());
        if (cashboxEntity == null) {
            throw new IllegalStateException("Cashbox not found: " + type);
        }

        BigDecimal newBalance;
        if (direction == MovementDirection.in) {
            newBalance = cashboxEntity.getBalance().add(amount);
        } else {
            newBalance = cashboxEntity.getBalance().subtract(amount);
            // F-04 : une caisse ne peut jamais passer sous zéro (garde applicative,
            // doublée d'un CHECK en base). Bloque retraits et décaissements insuffisants.
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException(
                    "Solde insuffisant dans la caisse " + type.label()
                        + " : solde " + cashboxEntity.getBalance()
                        + " FCFA, sortie demandée " + amount + " FCFA.");
            }
        }
        cashboxEntity.setBalance(newBalance);
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

        // F-35 : le mouvement renvoyé doit porter le solde RÉEL après écriture,
        // pas l'ancien état lu au début. On reconstruit un Cashbox à jour.
        Cashbox updatedCashbox = new Cashbox(
            cashboxEntity.getId(), cashboxEntity.getType(),
            cashboxEntity.getBalance(), cashboxEntity.getUpdatedAt());
        return new CashboxMovement(
            movement.getId(), updatedCashbox, movement.getMovementDate(),
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
