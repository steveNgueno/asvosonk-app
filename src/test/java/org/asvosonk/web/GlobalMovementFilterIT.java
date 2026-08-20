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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: the "filter" mode of the global search (by module and/or member)
 * threw <em>"could not determine data type of parameter $3"</em> and returned an
 * HTTP 500 — every optional parameter now carries an explicit CAST so PostgreSQL
 * can type it in the {@code ? IS NULL} position.
 */
@SpringBootTest
@Transactional
class GlobalMovementFilterIT extends AbstractIntegrationTest {

    @Autowired GlobalMovementViewRepository repository;
    @Autowired EntityManager em;

    private Long alphaId;
    private Long betaId;

    @BeforeEach
    void seed() {
        alphaId = insertMember("Alpha Filtre");
        betaId  = insertMember("Beta Filtre");
        insertSanction(alphaId);
        insertSanction(betaId);
        em.flush();
    }

    @Test
    void searchWithoutAnyFilterRuns() {
        assertThat(repository.search(null, null, null, null)).isNotNull();
    }

    @Test
    void searchByMemberKeepsOnlyThatMember() {
        List<GlobalMovementView> result = repository.search(alphaId, null, null, null);
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(r -> assertThat(r.getMemberId()).isEqualTo(alphaId));
    }

    @Test
    void searchByModuleKeepsOnlyThatModule() {
        List<GlobalMovementView> result = repository.search(null, "sanction", null, null);
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(r -> assertThat(r.getModule()).isEqualTo("sanction"));
    }

    @Test
    void searchByPeriodRuns() {
        List<GlobalMovementView> result = repository.search(
            null, null, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertThat(result).isNotEmpty();
    }

    @Test
    void searchByMemberAndModuleCombines() {
        List<GlobalMovementView> result = repository.search(betaId, "sanction", null, null);
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(r -> {
            assertThat(r.getMemberId()).isEqualTo(betaId);
            assertThat(r.getModule()).isEqualTo("sanction");
        });
    }

    private Long insertMember(String name) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES (:n, CURRENT_DATE) RETURNING id")
            .setParameter("n", name)
            .getSingleResult()).longValue();
    }

    private void insertSanction(Long memberId) {
        em.createNativeQuery(
                "INSERT INTO sanction (member_id, sanction_date, amount, reason) "
              + "VALUES (:m, CURRENT_DATE, 500, 'test filtre')")
            .setParameter("m", memberId)
            .executeUpdate();
    }
}
