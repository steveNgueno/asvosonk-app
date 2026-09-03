package org.asvosonk.bank.domain.repository;

import org.asvosonk.bank.domain.model.LoanRepayment;

import java.math.BigDecimal;
import java.util.List;

public interface LoanRepaymentRepository {

    LoanRepayment save(LoanRepayment repayment);

    List<LoanRepayment> findByLoanId(Long loanId);

    BigDecimal getTotalRepaidByLoanId(Long loanId);

    /** Remboursements encaissés pendant une séance, dans leur ordre de saisie. */
    List<LoanRepayment> findBySessionId(Long sessionId);

    /** Total des remboursements encaissés pendant une séance ; zéro si aucun. */
    BigDecimal getTotalRepaidBySessionId(Long sessionId);
}
