package org.asvosonk.bank.infrastructure.persistence.repository;

import org.asvosonk.bank.domain.valueobject.LoanStatus;
import org.asvosonk.bank.infrastructure.persistence.entity.LoanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SpringDataLoanRepository extends JpaRepository<LoanEntity, Long> {
    List<LoanEntity> findByMemberIdOrderByLoanDateDesc(Long memberId);
    long countByMemberIdAndStatus(Long memberId, LoanStatus status);
    List<LoanEntity> findByStatusAndDueDateBefore(LoanStatus status, LocalDate date);
    List<LoanEntity> findByMemberIdAndStatus(Long memberId, LoanStatus status);
}
