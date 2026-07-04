package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use case: list sanctions with optional filtering by member or status.
 */
@Service
@RequiredArgsConstructor
public class ListSanctionsUseCase {

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<SanctionEntity> findByMember(Long memberId) {
        TypedQuery<SanctionEntity> query = entityManager.createQuery(
            "SELECT s FROM SanctionEntity s WHERE s.member.id = :memberId ORDER BY s.sanctionDate DESC",
            SanctionEntity.class);
        query.setParameter("memberId", memberId);
        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public List<SanctionEntity> findByStatus(SanctionStatus status) {
        TypedQuery<SanctionEntity> query = entityManager.createQuery(
            "SELECT s FROM SanctionEntity s WHERE s.status = :status ORDER BY s.sanctionDate DESC",
            SanctionEntity.class);
        query.setParameter("status", status);
        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public List<SanctionEntity> findAll() {
        return entityManager.createQuery(
            "SELECT s FROM SanctionEntity s ORDER BY s.sanctionDate DESC",
            SanctionEntity.class).getResultList();
    }
}
