package org.asvosonk.bank.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.domain.model.LoanRepayment;
import org.asvosonk.bank.domain.repository.LoanRepaymentRepository;
import org.asvosonk.bank.infrastructure.persistence.entity.LoanRepaymentEntity;
import org.asvosonk.bank.infrastructure.persistence.mapper.LoanRepaymentMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaLoanRepaymentRepository implements LoanRepaymentRepository {

    private final SpringDataLoanRepaymentRepository springData;

    @Override
    public LoanRepayment save(LoanRepayment repayment) {
        LoanRepaymentEntity entity = LoanRepaymentMapper.toEntity(repayment);
        if (repayment.getId() != null) {
            entity.setId(repayment.getId());
        }
        return LoanRepaymentMapper.toDomain(springData.save(entity));
    }

    @Override
    public List<LoanRepayment> findByLoanId(Long loanId) {
        return springData.findByLoanId(loanId).stream()
            .map(LoanRepaymentMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public BigDecimal getTotalRepaidByLoanId(Long loanId) {
        BigDecimal total = springData.sumAmountByLoanId(loanId);
        return total != null ? total : BigDecimal.ZERO;
    }

    @org.springframework.stereotype.Repository
    interface SpringDataLoanRepaymentRepository extends JpaRepository<LoanRepaymentEntity, Long> {
        List<LoanRepaymentEntity> findByLoanId(Long loanId);

        @Query("SELECT SUM(r.amount) FROM LoanRepaymentEntity r WHERE r.loanId = :loanId")
        BigDecimal sumAmountByLoanId(Long loanId);
    }
}
