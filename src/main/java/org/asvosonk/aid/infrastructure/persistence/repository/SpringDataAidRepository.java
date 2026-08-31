package org.asvosonk.aid.infrastructure.persistence.repository;

import org.asvosonk.aid.domain.valueobject.AidStatus;
import org.asvosonk.aid.domain.valueobject.AidType;
import org.asvosonk.aid.infrastructure.persistence.entity.AidEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataAidRepository extends JpaRepository<AidEntity, Long> {
    List<AidEntity> findAllByOrderByAidDateDescIdDesc();
    List<AidEntity> findByStatusOrderByAidDateDescIdDesc(AidStatus status);
    List<AidEntity> findByBeneficiaryIdOrderByAidDateDesc(Long beneficiaryId);
    List<AidEntity> findByTypeOrderByAidDateDesc(AidType type);
    List<AidEntity> findByBeneficiaryIdAndStatusOrderByAidDateDesc(Long beneficiaryId, AidStatus status);
    List<AidEntity> findByStatusOrderByAidDateAsc(AidStatus status);
}
