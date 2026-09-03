package org.asvosonk.bank.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.domain.model.Saving;
import org.asvosonk.bank.domain.repository.SavingRepository;
import org.asvosonk.bank.infrastructure.persistence.entity.SavingEntity;
import org.asvosonk.bank.infrastructure.persistence.mapper.SavingMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaSavingRepository implements SavingRepository {

    private final SpringDataSavingRepository springData;

    @Override
    public Saving save(Saving saving) {
        SavingEntity entity = SavingMapper.toEntity(saving);
        if (saving.getId() != null) {
            entity.setId(saving.getId());
        }
        return SavingMapper.toDomain(springData.save(entity));
    }

    @Override
    public List<Saving> findByMemberIdOrderByOperationDateDesc(Long memberId) {
        return springData.findByMemberIdOrderByOperationDateDesc(memberId).stream()
            .map(SavingMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public BigDecimal getTotalSavingsByMemberId(Long memberId) {
        BigDecimal total = springData.sumAmountByMemberId(memberId);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Override
    public List<Saving> findBySessionId(Long sessionId) {
        return springData.findBySessionIdOrderByIdAsc(sessionId).stream()
            .map(SavingMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public BigDecimal getTotalSavingsBySessionId(Long sessionId) {
        BigDecimal total = springData.sumAmountBySessionId(sessionId);
        return total != null ? total : BigDecimal.ZERO;
    }
}
