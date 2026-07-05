package org.asvosonk.security.infrastructure.persistence.repository;

import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataAppUserRepository extends JpaRepository<AppUserEntity, Long> {
    Optional<AppUserEntity> findByLogin(String login);

    @Query("SELECT u FROM AppUserEntity u JOIN FETCH u.role r JOIN FETCH r.permissions WHERE u.login = :login")
    Optional<AppUserEntity> findByLoginWithPermissions(String login);

    boolean existsByLogin(String login);
}
