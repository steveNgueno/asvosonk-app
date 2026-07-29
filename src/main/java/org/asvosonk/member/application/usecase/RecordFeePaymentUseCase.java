package org.asvosonk.member.application.usecase;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.asvosonk.member.domain.valueobject.FeeType;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.member.infrastructure.persistence.entity.MembershipFee;
import org.asvosonk.member.infrastructure.persistence.repository.MembershipFeeRepository;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Use case: record a fee payment for a member.
 * Caps the payment at amountDue to prevent overpayment.
 *
 * <p>F-03: when the paid fee is the {@code revolving_fund} membership fee, the
 * real amount collected is credited to the member's revolving fund balance, so
 * the fund only ever holds money that was actually paid in (no fictitious 5 000
 * starting capital).
 */
@Service
@RequiredArgsConstructor
public class RecordFeePaymentUseCase {

    private final MembershipFeeRepository feeRepository;
    private final EntityManager           entityManager;

    @Transactional
    public MembershipFee execute(Long memberId, FeeType feeType, BigDecimal amount) {
        // La ligne de frais est normalement créée à l'inscription du membre.
        // Si elle n'existe pas (membre importé/ancien, initialisation partielle),
        // on la crée à la volée avec le montant dû par défaut plutôt que d'échouer.
        MembershipFee fee = feeRepository.findByMemberIdAndFeeType(memberId, feeType)
            .orElseGet(() -> {
                MembershipFee f = new MembershipFee();
                f.setMember(entityManager.getReference(MemberEntity.class, memberId));
                f.setFeeType(feeType);
                f.setAmountDue(feeType.defaultAmount());
                f.setAmountPaid(BigDecimal.ZERO);
                return f;
            });

        BigDecimal previousPaid = fee.getAmountPaid();
        BigDecimal newAmountPaid = previousPaid.add(amount);

        // Cap at amountDue — no overpayment allowed
        if (newAmountPaid.compareTo(fee.getAmountDue()) >= 0) {
            newAmountPaid = fee.getAmountDue();
            fee.setPaymentDate(LocalDate.now());
        }

        fee.setAmountPaid(newAmountPaid);
        MembershipFee saved = feeRepository.save(fee);

        // F-03 : the revolving-fund fee actually feeds the fund balance. Credit
        // only the real delta collected this time (post-cap), so re-payments or
        // capped overpayments never inflate the fund beyond what was paid in.
        if (feeType == FeeType.revolving_fund) {
            BigDecimal credited = newAmountPaid.subtract(previousPaid);
            if (credited.compareTo(BigDecimal.ZERO) > 0) {
                creditRevolvingFund(memberId, credited);
            }
        }

        return saved;
    }

    /**
     * Credits {@code amount} to the member's revolving fund. Operates on the
     * managed entity directly (rather than through a domain-model round-trip)
     * so the JPA @Version optimistic-lock guard (F-16) keeps its real value
     * instead of being reset to 0 by a detached-entity rebuild.
     */
    private void creditRevolvingFund(Long memberId, BigDecimal amount) {
        List<RevolvingFundEntity> funds = entityManager.createQuery(
                "SELECT f FROM RevolvingFundEntity f WHERE f.member.id = :memberId",
                RevolvingFundEntity.class)
            .setParameter("memberId", memberId)
            .getResultList();

        RevolvingFundEntity fund;
        if (funds.isEmpty()) {
            fund = new RevolvingFundEntity();
            fund.setMember(entityManager.getReference(MemberEntity.class, memberId));
            fund.setBalance(amount);
            fund.setUpdatedAt(LocalDateTime.now());
            entityManager.persist(fund);
        } else {
            fund = funds.get(0);
            fund.setBalance(fund.getBalance().add(amount));
            fund.setUpdatedAt(LocalDateTime.now());
            entityManager.merge(fund);
        }
    }
}
