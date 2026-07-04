package org.asvosonk.bank.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.asvosonk.bank.domain.valueobject.LoanStatus;
import org.asvosonk.bank.infrastructure.persistence.entity.LoanEntity;
import org.asvosonk.bank.infrastructure.persistence.mapper.LoanMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaLoanRepository implements LoanRepository {

    private final SpringDataLoanRepository springData;

    @Override
    public Loan save(Loan loan) {
        LoanEntity entity = LoanMapper.toEntity(loan);
        if (loan.getId() != null) {
            entity.setId(loan.getId());
        }
        return LoanMapper.toDomain(springData.save(entity));
    }

    @Override
    public Optional<Loan> findById(Long id) {
        return springData.findById(id).map(LoanMapper::toDomain);
    }

    @Override
    public List<Loan> findByMemberIdOrderByLoanDateDesc(Long memberId) {
        return springData.findByMemberIdOrderByLoanDateDesc(memberId).stream()
            .map(LoanMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countActiveLoans(Long memberId) {
        return springData.countByMemberIdAndStatus(memberId, LoanStatus.active);
    }

    @Override
    public List<Loan> findOverdueLoans() {
        return springData.findByStatusAndDueDateBefore(LoanStatus.active, LocalDate.now()).stream()
            .map(LoanMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Loan> findByMemberIdAndStatus(Long memberId, LoanStatus status) {
        return springData.findByMemberIdAndStatus(memberId, status).stream()
            .map(LoanMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Loan> findAll() {
        return springData.findAll().stream()
            .map(LoanMapper::toDomain)
            .collect(Collectors.toList());
    }

    @org.springframework.stereotype.Repository
    interface SpringDataLoanRepository extends JpaRepository<LoanEntity, Long> {
        List<LoanEntity> findByMemberIdOrderByLoanDateDesc(Long memberId);
        long countByMemberIdAndStatus(Long memberId, LoanStatus status);
        List<LoanEntity> findByStatusAndDueDateBefore(LoanStatus status, LocalDate date);
        List<LoanEntity> findByMemberIdAndStatus(Long memberId, LoanStatus status);
    }
}
