package org.asvosonk.session.infrastructure.persistence.mapper;

import jakarta.persistence.EntityManager;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.session.domain.model.RevolvingFund;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundEntity;

import java.time.LocalDateTime;

/**
 * Mapper between RevolvingFund domain model and RevolvingFundEntity JPA entity.
 */
public class RevolvingFundMapper {

    public static RevolvingFund toDomain(RevolvingFundEntity entity) {
        if (entity == null) return null;
        return new RevolvingFund(
            entity.getId(),
            entity.getMember().getId(),
            entity.getBalance(),
            entity.getUpdatedAt()
        );
    }

    public static RevolvingFundEntity toEntity(RevolvingFund domain) {
        if (domain == null) return null;
        RevolvingFundEntity entity = new RevolvingFundEntity();
        entity.setId(domain.getId());
        entity.setBalance(domain.getBalance());
        entity.setUpdatedAt(domain.getUpdatedAt() != null ? domain.getUpdatedAt() : LocalDateTime.now());
        return entity;
    }

    /**
     * Converts a domain RevolvingFund to an entity, setting the MemberEntity reference.
     * Accepts an EntityManager to create a JPA proxy for the member without a DB query.
     */
    public static RevolvingFundEntity toEntity(RevolvingFund domain, EntityManager em) {
        if (domain == null) return null;
        RevolvingFundEntity entity = toEntity(domain);
        entity.setMember(em.getReference(MemberEntity.class, domain.getMemberId()));
        return entity;
    }
}
