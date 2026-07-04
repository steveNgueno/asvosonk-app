package org.asvosonk.common.infrastructure.persistence;

/**
 * Interface for mapping between domain models and JPA entities.
 *
 * @param <D> Domain model type
 * @param <E> JPA entity type
 */
public interface EntityMapper<D, E> {

    D toDomain(E entity);

    E toEntity(D domain);
}
