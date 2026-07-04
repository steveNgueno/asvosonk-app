package org.asvosonk.cashbox.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.application.service.CashboxService;
import org.asvosonk.cashbox.domain.model.CashboxMovement;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Use case: deposit money into a cashbox.
 * Credits the specified cashbox and records a movement.
 */
@Service
@RequiredArgsConstructor
public class DepositMoneyUseCase {

    private final CashboxService cashboxService;

    /**
     * Deposit an amount into a cashbox.
     *
     * @return the recorded movement, or null if amount is zero or negative
     */
    @Transactional
    public CashboxMovement execute(CashboxType type, BigDecimal amount, String reason,
                                   MovementOrigin origin, MeetingSessionEntity session,
                                   MemberEntity member, Long refId, AppUser user) {
        return cashboxService.record(type, MovementDirection.in, amount, reason, origin,
            session, member, refId, user);
    }
}
