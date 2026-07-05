package org.asvosonk.session.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.session.domain.model.RevolvingFund;
import org.asvosonk.session.domain.repository.RevolvingFundRepository;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundEntity;
import org.asvosonk.session.infrastructure.persistence.mapper.RevolvingFundMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaRevolvingFundRepository implements RevolvingFundRepository {

    private final SpringDataRevolvingFundRepository springData;

    @Override
    public Optional<RevolvingFund> findByMemberId(Long memberId) {
        return springData.findByMemberId(memberId).map(RevolvingFundMapper::toDomain);
    }

    @Override
    public RevolvingFund save(RevolvingFund fund) {
        RevolvingFundEntity entity = RevolvingFundMapper.toEntity(fund);
        return RevolvingFundMapper.toDomain(springData.save(entity));
    }

}
