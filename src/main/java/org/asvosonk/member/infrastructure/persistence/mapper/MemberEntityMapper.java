package org.asvosonk.member.infrastructure.persistence.mapper;

import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;

public class MemberEntityMapper {

    public static Member toDomain(MemberEntity entity) {
        if (entity == null) return null;
        return new Member(
            entity.getId(),
            entity.getFullName(),
            entity.getPhone(),
            entity.getJoinDate(),
            entity.isResident(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static MemberEntity toEntity(Member domain) {
        if (domain == null) return null;
        MemberEntity entity = new MemberEntity();
        entity.setId(domain.getId());
        entity.setFullName(domain.getFullName());
        entity.setPhone(domain.getPhone());
        entity.setJoinDate(domain.getJoinDate());
        entity.setResident(domain.isResident());
        entity.setStatus(domain.getStatus());
        return entity;
    }
}
