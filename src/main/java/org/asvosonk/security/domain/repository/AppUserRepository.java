package org.asvosonk.security.domain.repository;

import org.asvosonk.security.domain.model.AppUser;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository {

    Optional<AppUser> findById(Long id);

    Optional<AppUser> findByLogin(String login);

    List<AppUser> findAll();

    boolean existsByLogin(String login);

    AppUser save(AppUser appUser);

    void delete(AppUser appUser);
}
