package org.asvosonk.web;

import jakarta.persistence.EntityManager;
import org.asvosonk.support.AbstractIntegrationTest;
import org.asvosonk.web.infrastructure.persistence.entity.GlobalMovementView;
import org.asvosonk.web.infrastructure.persistence.repository.GlobalMovementViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-17: {@link GlobalMovementViewRepository#searchByKeyword} must actually
 * filter. The old {@code (:memberId IS NULL OR member_id = :memberId) OR ...}
 * short-circuited to TRUE whenever memberId was null (the common text-search
 * case), returning every recent movement instead of the matches.
 */
@SpringBootTest
@Transactional
class GlobalMovementSearchIT extends AbstractIntegrationTest {

    @Autowired GlobalMovementViewRepository repository;
    @Autowired EntityManager em;

    private Long alphaId;
    private Long betaId;

    @BeforeEach
    void seed() {
        // Two members, each with one sanction carrying a distinctive reason/status.
        alphaId = insertMember("Alpha Tester");
        betaId  = insertMember("Beta Tester");
        insertSanction(alphaId, "cotisation manquante");   // status 'unpaid'
        insertSanction(betaId,  "retard reunion");         // status 'unpaid'
        em.flush();
    }

    @Test
    void keywordWithoutMatchAndNoMemberReturnsNothing() {
        // The regression: null memberId + a keyword that matches no module/status
        // must return ZERO rows (previously returned all recent movements).
        List<GlobalMovementView> result =
            repository.searchByKeyword(null, "%zzz_no_such_token_zzz%");
        assertThat(result).isEmpty();
    }

    @Test
    void keywordMatchingModuleReturnsOnlyThatModule() {
        List<GlobalMovementView> result =
            repository.searchByKeyword(null, "%sanction%");
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(r -> assertThat(r.getModule()).isEqualTo("sanction"));
        // Both seeded sanctions surface.
        assertThat(result).extracting(GlobalMovementView::getMemberId)
            .contains(alphaId, betaId);
    }

    @Test
    void memberIdCrossReferenceIsPreserved() {
        // A numeric query still surfaces that member's own rows even when the
        // keyword itself matches no module/status.
        List<GlobalMovementView> result =
            repository.searchByKeyword(alphaId, "%zzz_no_such_token_zzz%");
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(r -> assertThat(r.getMemberId()).isEqualTo(alphaId));
    }

    private Long insertMember(String name) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES (:n, CURRENT_DATE) RETURNING id")
            .setParameter("n", name)
            .getSingleResult()).longValue();
    }

    private void insertSanction(Long memberId, String reason) {
        em.createNativeQuery(
                "INSERT INTO sanction (member_id, sanction_date, amount, reason) "
              + "VALUES (:m, CURRENT_DATE, 500, :r)")
            .setParameter("m", memberId)
            .setParameter("r", reason)
            .executeUpdate();
    }
}
