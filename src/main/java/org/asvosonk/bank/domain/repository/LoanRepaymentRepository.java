package org.asvosonk.bank.domain.repository;

import org.asvosonk.bank.domain.model.LoanRepayment;

import java.math.BigDecimal;
import java.util.List;

public interface LoanRepaymentRepository {

    LoanRepayment save(LoanRepayment repayment);

    List<LoanRepayment> findByLoanId(Long loanId);

    BigDecimal getTotalRepaidByLoanId(Long loanId);
}
