package org.asvosonk.bank;

import jakarta.persistence.EntityManager;
import org.asvosonk.bank.application.usecase.CreateLoanUseCase;
import org.asvosonk.bank.application.usecase.RecordLoanRepaymentUseCase;
import org.asvosonk.bank.application.usecase.RecordSavingUseCase;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.model.Permission;
import org.asvosonk.security.domain.model.Role;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionStepService;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les erreurs de gabarit Thymeleaf (fragment manquant, paramètre mal passé,
 * expression invalide) ne se voient qu'au rendu : ce test ouvre réellement les
 * pages de la Banque Annuelle — feuille de séance, étape clôturée, rapport et
 * rubrique du menu — avec des données non nulles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnnualBankPagesRenderIT extends AbstractIntegrationTest {

    @Autowired MockMvc                   mockMvc;
    @Autowired RecordSavingUseCase       recordSavingUseCase;
    @Autowired CreateLoanUseCase         createLoanUseCase;
    @Autowired RecordLoanRepaymentUseCase recordLoanRepaymentUseCase;
    @Autowired SessionStepService        sessionStepService;
    @Autowired AppUserRepository         appUserRepository;
    @Autowired EntityManager             em;

    private AppUser admin;
    private Long memberId;
    private Long sessionId;

    /** Principal réel portant les autorisations demandées par les contrôleurs. */
    private UserDetailsImpl principal() {
        Set<Permission> perms = Arrays.stream(new String[]{
                "SESSION_VIEW", "SESSION_CLOSE", "BANK_VIEW", "BANK_SAVING_RECORD",
                "BANK_LOAN_CREATE", "BANK_LOAN_REPAYMENT", "MEMBER_VIEW",
                "CASHBOX_VIEW", "CASHBOX_MANUAL_MOVEMENT"})
            .map(code -> new Permission(null, code, code))
            .collect(Collectors.toSet());
        Role role = new Role(1, "TEST_ROLE", "test", perms);
        return new UserDetailsImpl(new AppUser(1L, "tester", "hash", role, null,
            true, null, 0, null, null, null));
    }

    @BeforeEach
    void seed() {
        admin = appUserRepository.findByLogin("admin").orElseThrow();

        memberId = ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES ('Rendu', CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();

        MeetingSessionEntity session = new MeetingSessionEntity();
        session.setSessionDate(LocalDate.now());
        session.setStatus(SessionStatus.open);
        session.setCurrentStepEnum(SessionStep.BANQUE_ANNUELLE_OPEN);
        em.persist(session);
        em.flush();
        sessionId = session.getId();

        recordSavingUseCase.execute(memberId, new BigDecimal("20000"), LocalDate.now(), session, admin);
        Loan loan = createLoanUseCase.execute(memberId, new BigDecimal("6000"), session, admin);
        recordLoanRepaymentUseCase.execute(loan.getId(), new BigDecimal("2000"), session, admin);
        em.flush();
    }

    @Test
    void sessionSheetRendersWhileTheStepIsOpen() throws Exception {
        mockMvc.perform(get("/sessions/" + sessionId).with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Banque Annuelle")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Effet net sur la caisse Banque")));
    }

    @Test
    void sessionPageAndReportRenderOnceTheStepIsClosed() throws Exception {
        sessionStepService.transitionToNext(sessionId, admin, SessionStep.BANQUE_ANNUELLE_OPEN);

        mockMvc.perform(get("/sessions/" + sessionId).with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Effet net sur la caisse Banque")));

        mockMvc.perform(get("/sessions/" + sessionId + "/report").with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Emprunts décaissés")));
    }

    @Test
    void bankSectionOfTheMenuRenders() throws Exception {
        mockMvc.perform(get("/bank").with(user(principal())))
            .andExpect(status().isOk());

        mockMvc.perform(get("/bank/members/" + memberId).with(user(principal())))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("Historique des remboursements")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Reste à payer")));
    }
}
