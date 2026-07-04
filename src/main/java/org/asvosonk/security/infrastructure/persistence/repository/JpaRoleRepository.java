package org.asvosonk.security.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.security.domain.model.Role;
import org.asvosonk.security.domain.repository.RoleRepository;
import org.asvosonk.security.infrastructure.persistence.entity.RoleEntity;
import org.asvosonk.security.infrastructure.persistence.mapper.UserEntityMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaRoleRepository implements RoleRepository {

    private final SpringDataRoleRepository springData;

    @Override
    public Optional<Role> findById(Integer id) {
        return springData.findById(id).map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<Role> findByName(String name) {
        return springData.findByName(name).map(UserEntityMapper::toDomain);
    }

    @Override
    public List<Role> findAll() {
        return springData.findAll().stream()
            .map(UserEntityMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Role save(Role role) {
        RoleEntity entity = UserEntityMapper.toEntity(role);
        return UserEntityMapper.toDomain(springData.save(entity));
    }

    @org.springframework.stereotype.Repository
    interface SpringDataRoleRepository extends JpaRepository<RoleEntity, Integer> {
        Optional<RoleEntity> findByName(String name);
    }
}
