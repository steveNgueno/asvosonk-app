package org.asvosonk.session.presentation.response;

import lombok.*;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.application.service.RevolvingFundService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Chiffres de la présence d'une séance, tels qu'affichés dans la séance et dans
 * le rapport.
 *
 * <p>Le total remis au trésorier n'est volontairement pas calculé ici : il ne se
 * déduit pas de la seule présence. Il vaut la somme des entrées de caisse de la
 * séance moins ses sorties (sanctions encaissées, dons, dépenses du jour
 * compris) et est porté par {@code session_report.total_to_treasurer}.</p>
 */
@Getter @Setter
@Builder
public class SessionCloseResult {

    private MeetingSessionEntity session;
    private Member beneficiary;

    // Effectifs
    private int totalCotisants;
    private int presentCount;
    private int fundCoveredCount;
    private int defaultCount;

    // Tontine de présence
    private BigDecimal grossTontine;       // somme des parts de tontine cotisées
    private BigDecimal sanctionDeductions; // retenues sur le bénéficiaire
    private BigDecimal netTontine;         // ce que le bénéficiaire perçoit

    // Caisses alimentées par la séance
    private BigDecimal totalDevelopment;
    private BigDecimal totalBeveragePool;
    private BigDecimal actualBeverageCost;
    private BigDecimal beverageReliquat;

    /** Argent revenu dans les fonds de roulement des membres. */
    private BigDecimal returnToFund;
    /** Rattrapages dus aux bénéficiaires des séances dont un échec a été recouvert. */
    private BigDecimal recoveryTotal;
    /** Part de la tontine prélevée pour remettre le bénéficiaire à jour. */
    private BigDecimal fundCatchUp;

    private List<RevolvingFundService.AttendanceResult> attendanceResults;

    /** Membres à jour : ni couverts par le fond, ni en échec. */
    public int getUpToDateCount() {
        return totalCotisants - fundCoveredCount - defaultCount;
    }
}
