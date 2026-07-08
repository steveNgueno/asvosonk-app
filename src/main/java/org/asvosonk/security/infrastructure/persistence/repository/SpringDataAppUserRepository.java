package org.asvosonk.security.infrastructure.persistence.repository;

import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataAppUserRepository extends JpaRepository<AppUserEntity, Long> {
    Optional<AppUserEntity> findByLogin(String login);

    @Query("SELECT u FROM AppUserEntity u JOIN FETCH u.role r JOIN FETCH r.permissions WHERE u.login = :login")
    Optional<AppUserEntity> findByLoginWithPermissions(String login);

    @Query("SELECT DISTINCT u FROM AppUserEntity u LEFT JOIN FETCH u.member LEFT JOIN FETCH u.role r LEFT JOIN FETCH r.permissions")
    List<AppUserEntity> findAllWithMemberAndRole();

    boolean existsByLogin(String login);
}
