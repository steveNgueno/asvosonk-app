package org.asvosonk.report;

import org.asvosonk.report.application.service.ReportModuleUnavailableException;
import org.asvosonk.report.application.service.ReportService;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.model.Permission;
import org.asvosonk.security.domain.model.Role;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Un échec de génération ramène sur la page Rapports avec un message lisible,
 * au lieu de la page d'erreur 500 générique ; un générateur absent du serveur
 * est signalé comme tel.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportFailureIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @MockBean  ReportService reportService;

    private static UserDetailsImpl reporter() {
        Set<Permission> perms = Set.of(new Permission(null, "REPORT_SESSION", "REPORT_SESSION"));
        return new UserDetailsImpl(new AppUser(1L, "tester", "hash",
            new Role(1, "TEST", "test", perms), null, true, null, 0, null, null, null));
    }

    @Test
    void moduleAbsentSignaleSurLaPageRapports() throws Exception {
        when(reportService.generateReport(any(), any(), any()))
            .thenThrow(new ReportModuleUnavailableException());

        mockMvc.perform(post("/reports/generate").param("type", "session")
                .with(user(reporter())).with(csrf()))
            .andExpect(redirectedUrl("/reports"))
            .andExpect(flash().attribute("errorMessage", containsString("n'est pas installé")));
    }

    @Test
    void echecDeGenerationSignaleSansPage500() throws Exception {
        when(reportService.generateReport(any(), any(), any()))
            .thenThrow(new IOException("process failed"));

        mockMvc.perform(post("/reports/generate").param("type", "session")
                .with(user(reporter())).with(csrf()))
            .andExpect(redirectedUrl("/reports"))
            .andExpect(flash().attribute("errorMessage", containsString("La génération du rapport a échoué")));
    }
}
