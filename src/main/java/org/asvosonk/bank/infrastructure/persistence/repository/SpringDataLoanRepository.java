package org.asvosonk.bank.infrastructure.persistence.repository;

import org.asvosonk.bank.domain.valueobject.LoanStatus;
import org.asvosonk.bank.infrastructure.persistence.entity.LoanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataLoanRepository extends JpaRepository<LoanEntity, Long> {
    List<LoanEntity> findByMemberIdOrderByLoanDateDesc(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanEntity l where l.id = :id")
    Optional<LoanEntity> findByIdForUpdate(Long id);
    long countByMemberIdAndStatus(Long memberId, LoanStatus status);
    long countByMemberIdAndStatusNot(Long memberId, LoanStatus status);
    List<LoanEntity> findByStatusAndDueDateBefore(LoanStatus status, LocalDate date);
    List<LoanEntity> findByMemberIdAndStatus(Long memberId, LoanStatus status);
}
