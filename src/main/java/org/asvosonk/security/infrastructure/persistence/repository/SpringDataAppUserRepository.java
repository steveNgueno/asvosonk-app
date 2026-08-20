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

    // Le membre lié est chargé lui aussi : le mapper vers le modèle de domaine le
    // lit systématiquement, et sans ce fetch il n'est exploitable que dans une
    // transaction ouverte (open-in-view étant désactivé).
    @Query("SELECT u FROM AppUserEntity u LEFT JOIN FETCH u.member JOIN FETCH u.role r JOIN FETCH r.permissions WHERE u.login = :login")
    Optional<AppUserEntity> findByLoginWithPermissions(String login);

    @Query("SELECT DISTINCT u FROM AppUserEntity u LEFT JOIN FETCH u.member LEFT JOIN FETCH u.role r LEFT JOIN FETCH r.permissions")
    List<AppUserEntity> findAllWithMemberAndRole();

    /**
     * Loads a user with its member and role graph.
     *
     * <p>{@code findById} alone returns the linked member as a lazy proxy; since
     * {@code open-in-view} is disabled, mapping it to the domain model outside a
     * transaction threw <em>"could not initialize proxy — no Session"</em>, which
     * made the user edit screen fail with an HTTP 500 and broke the deactivate
     * action. Fetching the graph up front keeps the mapper safe wherever it runs.
     */
    @Query("SELECT u FROM AppUserEntity u LEFT JOIN FETCH u.member LEFT JOIN FETCH u.role r LEFT JOIN FETCH r.permissions WHERE u.id = :id")
    Optional<AppUserEntity> findByIdWithMemberAndRole(Long id);

    boolean existsByLogin(String login);
}
