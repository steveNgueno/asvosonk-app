package org.asvosonk.bank.domain.repository;

import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.valueobject.LoanStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LoanRepository {

    Loan save(Loan loan);

    Optional<Loan> findById(Long id);

    /**
     * Loads a loan under a PESSIMISTIC_WRITE lock (F-49). Concurrent repayment
     * submissions on the same loan are then serialized at the DB row level, so
     * a double-click can never both read the same "already repaid" total and
     * both pass the F-19 remaining-balance check.
     */
    Optional<Loan> findByIdForUpdate(Long id);

    List<Loan> findByMemberIdOrderByLoanDateDesc(Long memberId);

    long countActiveLoans(Long memberId);

    List<Loan> findOverdueLoans();

    List<Loan> findByMemberIdAndStatus(Long memberId, LoanStatus status);

    List<Loan> findAll();

    /** Emprunts encore dus — actifs ou en retard — tous membres confondus. */
    List<Loan> findOutstanding();

    /** Emprunts accordés pendant une séance, dans leur ordre de saisie. */
    List<Loan> findBySessionId(Long sessionId);

    /** Total décaissé en emprunts pendant une séance ; zéro si aucun. */
    BigDecimal getTotalLoanedBySessionId(Long sessionId);
}
