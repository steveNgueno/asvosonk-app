package org.asvosonk.aid;

import jakarta.persistence.EntityManager;
import org.asvosonk.aid.application.usecase.CreateAidUseCase;
import org.asvosonk.aid.domain.model.Aid;
import org.asvosonk.aid.domain.repository.AidContributionRepository;
import org.asvosonk.aid.domain.valueobject.AidType;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aides : pas de date future, pas de doublon (même membre, même motif, même
 * date), et l'aide « décès d'un membre », versée à la famille, reste possible
 * pour un membre déjà enregistré comme décédé.
 */
@SpringBootTest
@Transactional
class AidRulesIT extends AbstractIntegrationTest {

    private static final BigDecimal TOTAL = new BigDecimal("6000");
    private static final BigDecimal SHARE = new BigDecimal("2000");

    @Autowired CreateAidUseCase          createAid;
    @Autowired AidContributionRepository contributionRepository;
    @Autowired EntityManager             em;

    private Long memberA;
    private Long memberB;

    @BeforeEach
    void seed() {
        memberA = insertMember("Aide A", "active");
        memberB = insertMember("Aide B", "active");
        em.createNativeQuery("""
                INSERT INTO meeting_session (session_date, status, current_step)
                VALUES (CURRENT_DATE, 'open', 'CREATED')
                """).executeUpdate();
        em.flush();
    }

    @Test
    void uneAideNeSeDatePasDansLeFutur() {
        assertThatThrownBy(() -> createAid.execute(memberA, AidType.naissance,
                LocalDate.now().plusDays(1), TOTAL, SHARE, null, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("futur");
    }

    @Test
    void uneAideEnDoubleEstRefusee() {
        createAid.execute(memberA, AidType.naissance, LocalDate.now(), TOTAL, SHARE, null, null);

        assertThatThrownBy(() -> createAid.execute(memberA, AidType.naissance, LocalDate.now(),
                TOTAL, SHARE, null, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("déjà enregistrée");
    }

    @Test
    void leDecesDUnMembreDejaDecedeSeVerseALaFamille() {
        Long deceased = insertMember("Aide Décédé", "deceased");

        Aid aid = createAid.execute(deceased, AidType.deces_membre, LocalDate.now(),
            new BigDecimal("340000"), SHARE, "à la famille", null);

        assertThat(aid.getId()).isNotNull();
        // Le défunt ne figure pas parmi les membres appelés à cotiser.
        assertThat(contributionRepository.findByAidId(aid.getId()))
            .noneMatch(c -> c.getMemberId().equals(deceased));
    }

    @Test
    void unAutreMotifResteReserveAuxMembresActifs() {
        Long deceased = insertMember("Aide Décédé 2", "deceased");

        assertThatThrownBy(() -> createAid.execute(deceased, AidType.naissance, LocalDate.now(),
                TOTAL, SHARE, null, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("membres actifs");
    }

    private Long insertMember(String name, String status) {
        return ((Number) em.createNativeQuery("""
                INSERT INTO member (full_name, join_date, status)
                VALUES (:n, CURRENT_DATE, CAST(:s AS member_status)) RETURNING id
                """)
            .setParameter("n", name)
            .setParameter("s", status)
            .getSingleResult()).longValue();
    }
}
