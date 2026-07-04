package org.asvosonk.bank.domain.repository;

import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.valueobject.LoanStatus;

import java.util.List;
import java.util.Optional;

public interface LoanRepository {

    Loan save(Loan loan);

    Optional<Loan> findById(Long id);

    List<Loan> findByMemberIdOrderByLoanDateDesc(Long memberId);

    long countActiveLoans(Long memberId);

    List<Loan> findOverdueLoans();

    List<Loan> findByMemberIdAndStatus(Long memberId, LoanStatus status);

    List<Loan> findAll();
}
