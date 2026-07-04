package org.asvosonk.security.domain.repository;

import org.asvosonk.security.domain.model.Role;

import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    Optional<Role> findById(Integer id);

    Optional<Role> findByName(String name);

    List<Role> findAll();

    Role save(Role role);
}
