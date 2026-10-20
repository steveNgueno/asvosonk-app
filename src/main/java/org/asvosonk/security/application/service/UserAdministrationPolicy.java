package org.asvosonk.security.application.service;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Qui peut administrer quel compte.
 *
 * <p>SECRETARY, TREASURER et PRESIDENT portent les mêmes permissions
 * (USER_CREATE, USER_EDIT, USER_DEACTIVATE comprises) : une règle fondée sur une
 * permission ne les distingue donc pas. L'ancien contrôle « seul le PRESIDENT »
 * testait USER_DEACTIVATE, que le SECRETARY possède — il pouvait ainsi changer
 * le mot de passe de n'importe qui, se promouvoir PRESIDENT ou créer un compte
 * PRESIDENT. Les règles portent désormais sur le <strong>rôle</strong> :</p>
 * <ul>
 *   <li>seul un PRESIDENT attribue ou retire le rôle PRESIDENT, et agit sur un
 *       compte PRESIDENT ;</li>
 *   <li>personne ne modifie son propre rôle ni ne désactive son propre compte ;</li>
 *   <li>seul un PRESIDENT change le mot de passe d'un autre utilisateur.</li>
 * </ul>
 *
 * <p>L'utilisateur qui agit est relu en base : le principal de la session est un
 * instantané pris à la connexion et peut porter un rôle périmé.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdministrationPolicy {

    public static final String PRESIDENT = "PRESIDENT";

    private final AppUserRepository appUserRepository;

    /** Création d'un compte portant le rôle {@code newRole}. */
    public void requireCanCreate(Long actorId, String newRole) {
        AppUser actor = load(actorId);
        if (PRESIDENT.equals(newRole) && !isPresident(actor)) {
            throw new BusinessRuleException("Seul un PRESIDENT peut créer un compte PRESIDENT.");
        }
    }

    /** Changement du rôle du compte {@code targetId} en {@code newRole}. */
    public void requireCanAssignRole(Long actorId, Long targetId, String newRole) {
        AppUser actor = load(actorId);
        AppUser target = load(targetId);
        if (actor.getId().equals(target.getId())) {
            throw new BusinessRuleException("Vous ne pouvez pas modifier votre propre rôle.");
        }
        boolean touchesPresident = PRESIDENT.equals(newRole) || isPresident(target);
        if (touchesPresident && !isPresident(actor)) {
            throw new BusinessRuleException(
                "Seul un PRESIDENT peut attribuer ou retirer le rôle PRESIDENT.");
        }
    }

    /** Changement du mot de passe du compte {@code targetId}. */
    public void requireCanChangePassword(Long actorId, Long targetId) {
        AppUser actor = load(actorId);
        if (!actor.getId().equals(targetId) && !isPresident(actor)) {
            throw new BusinessRuleException(
                "Seul le PRESIDENT peut changer le mot de passe d'un autre utilisateur.");
        }
    }

    /** Désactivation du compte {@code targetId}. */
    public void requireCanDeactivate(Long actorId, Long targetId) {
        AppUser actor = load(actorId);
        AppUser target = load(targetId);
        if (actor.getId().equals(target.getId())) {
            throw new BusinessRuleException("Vous ne pouvez pas désactiver votre propre compte.");
        }
        if (isPresident(target)) {
            if (!isPresident(actor)) {
                throw new BusinessRuleException("Seul un PRESIDENT peut désactiver un compte PRESIDENT.");
            }
            requireAnotherActivePresident(target);
        }
    }

    /**
     * Refuse de retirer le dernier compte PRESIDENT actif : l'association se
     * retrouverait sans personne pour administrer les comptes PRESIDENT.
     */
    public void requireAnotherActivePresident(AppUser target) {
        long otherActivePresidents = appUserRepository.findAll().stream()
            .filter(u -> u.isActive() && isPresident(u) && !u.getId().equals(target.getId()))
            .count();
        if (otherActivePresidents == 0) {
            throw new BusinessRuleException(
                "Impossible : « " + target.getLogin() + " » est le dernier compte PRESIDENT actif.");
        }
    }

    public static boolean isPresident(AppUser user) {
        return user.getRole() != null && PRESIDENT.equals(user.getRole().getName());
    }

    private AppUser load(Long userId) {
        return appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));
    }
}
