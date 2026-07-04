package org.asvosonk.member.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.member.domain.valueobject.FeeType;
import org.asvosonk.member.infrastructure.persistence.entity.MembershipFee;
import org.asvosonk.member.infrastructure.persistence.repository.MembershipFeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Use case: record a fee payment for a member.
 * Caps the payment at amountDue to prevent overpayment.
 */
@Service
@RequiredArgsConstructor
public class RecordFeePaymentUseCase {

    private final MembershipFeeRepository feeRepository;

    @Transactional
    public MembershipFee execute(Long memberId, FeeType feeType, BigDecimal amount) {
        MembershipFee fee = feeRepository.findByMemberIdAndFeeType(memberId, feeType)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Cotisation " + feeType.label() + " pour le membre", memberId));

        BigDecimal newAmountPaid = fee.getAmountPaid().add(amount);

        // Cap at amountDue — no overpayment allowed
        if (newAmountPaid.compareTo(fee.getAmountDue()) >= 0) {
            newAmountPaid = fee.getAmountDue();
            fee.setPaymentDate(LocalDate.now());
        }

        fee.setAmountPaid(newAmountPaid);
        return feeRepository.save(fee);
    }
}
