package org.asvosonk.bank.infrastructure.persistence.repository;

import org.asvosonk.bank.infrastructure.persistence.entity.LoanRepaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SpringDataLoanRepaymentRepository extends JpaRepository<LoanRepaymentEntity, Long> {
    List<LoanRepaymentEntity> findByLoanId(Long loanId);

    @Query("SELECT SUM(r.amount) FROM LoanRepaymentEntity r WHERE r.loanId = :loanId")
    BigDecimal sumAmountByLoanId(Long loanId);
}
