package org.asvosonk.security.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.asvosonk.security.infrastructure.persistence.mapper.UserEntityMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaAppUserRepository implements AppUserRepository {

    private final SpringDataAppUserRepository springData;

    @Override
    public Optional<AppUser> findById(Long id) {
        return springData.findById(id).map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<AppUser> findByLogin(String login) {
        return springData.findByLoginWithPermissions(login).map(UserEntityMapper::toDomain);
    }

    @Override
    public List<AppUser> findAll() {
        return springData.findAllWithMemberAndRole().stream()
            .map(UserEntityMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public boolean existsByLogin(String login) {
        return springData.existsByLogin(login);
    }

    @Override
    public AppUser save(AppUser appUser) {
        AppUserEntity entity = UserEntityMapper.toEntity(appUser);
        return UserEntityMapper.toDomain(springData.save(entity));
    }

    @Override
    public void delete(AppUser appUser) {
        springData.deleteById(appUser.getId());
    }

}
