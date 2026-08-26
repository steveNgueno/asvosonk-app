package org.asvosonk.aid.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.aid.domain.model.Aid;
import org.asvosonk.aid.domain.model.AidContribution;
import org.asvosonk.aid.domain.repository.AidContributionRepository;
import org.asvosonk.aid.domain.repository.AidRepository;
import org.asvosonk.aid.domain.valueobject.AidType;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.application.usecase.RequireOpenSessionUseCase;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Enregistre une aide lorsqu'elle intervient, en séance.
 *
 * <p>La réunion choisit le membre concerné, le motif (décès, naissance,
 * mariage…), la somme remise — proposée selon les statuts mais modifiable —
 * et la part que chaque membre devra recouvrir (la division et l'arrondi
 * sont faits par l'assemblée elle-même).</p>
 *
 * <p>À la création, un instantané des membres actifs est figé : chaque membre
 * concerné reçoit une part à recouvrir. Un membre qui adhère plus tard n'a
 * pas de ligne ici et n'est donc pas concerné par l'aide en cours.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateAidUseCase {

    private final AidRepository            aidRepository;
    private final AidContributionRepository aidContributionRepository;
    private final MemberRepository          memberRepository;
    private final RequireOpenSessionUseCase requireOpenSession;

    @Transactional
    public Aid execute(Long beneficiaryId, AidType type, LocalDate aidDate,
                       BigDecimal totalAmount, BigDecimal sharePerMember,
                       String description, AppUser recordedBy) {

        MeetingSessionEntity session = requireOpenSession.require("l'enregistrement d'une aide");

        if (beneficiaryId == null) {
            throw new BusinessRuleException("Le membre concerné par l'aide est obligatoire.");
        }
        Member beneficiary = memberRepository.findById(beneficiaryId).orElseThrow(
            () -> new BusinessRuleException("Membre introuvable : " + beneficiaryId));
        if (!beneficiary.isActive()) {
            throw new BusinessRuleException(
                "L'aide ne concerne que les membres actifs : "
              + beneficiary.getFullName() + " est " + beneficiary.getStatusLabel() + ".");
        }
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new BusinessRuleException("La somme de l'aide doit être strictement positive.");
        }
        if (sharePerMember == null || sharePerMember.signum() < 0) {
            throw new BusinessRuleException(
                "La part par membre doit être positive ou nulle : c'est elle que chaque "
              + "membre devra recouvrir.");
        }

        List<Member> concerned = memberRepository.findAllActive();
        if (concerned.stream().noneMatch(m -> m.getId().equals(beneficiaryId))) {
            // déjà couvert par le filtre ci-dessus, garde-fou explicite :
            throw new BusinessRuleException(
                "Le membre concerné doit être un membre actif de l'association.");
        }
        if (concerned.size() < 2) {
            throw new BusinessRuleException(
                "Impossible de répartir une aide : il faut au moins deux membres actifs.");
        }

        Aid aid = new Aid(null, beneficiaryId,
            type != null ? type : AidType.autre,
            aidDate != null ? aidDate : LocalDate.now(),
            description, totalAmount, sharePerMember,
            null, session.getId(), null, null);
        Aid saved = aidRepository.save(aid);

        // Instantané : une part à recouvrir pour chaque membre actif du jour.
        List<AidContribution> shares = new ArrayList<>();
        for (Member member : concerned) {
            shares.add(new AidContribution(null, saved.getId(), member.getId(),
                sharePerMember, BigDecimal.ZERO, null, null, null,
                null, null, null));
        }
        shares.forEach(share -> aidContributionRepository.save(share));

        log.info("Aide {} enregistrée pour {} ({} FCFA, part/membre {} FCFA, "
                + "{} membres concernés) en séance {}.",
            saved.getType(), beneficiary.getFullName(), totalAmount, sharePerMember,
            concerned.size(), session.getSessionDate());
        return saved;
    }
}
