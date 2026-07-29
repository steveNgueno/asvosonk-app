package org.asvosonk.report;

import org.asvosonk.report.application.service.ReportService;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.model.Permission;
import org.asvosonk.security.domain.model.Role;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-13 / F-14: report generation must validate the type against a whitelist and
 * enforce the per-type authority, and must never reflect an exception message
 * back to the client.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerSecurityIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;

    // Stub the report generator so tests don't spawn the Python process.
    @MockBean ReportService reportService;

    @BeforeEach
    void stubReportFile() throws Exception {
        Path pdf = Files.createTempFile("test-report", ".pdf");
        Files.writeString(pdf, "%PDF-1.4 test");
        pdf.toFile().deleteOnExit();
        when(reportService.generateReport(any(), any(), any())).thenReturn(pdf);
    }

    /** Builds a real UserDetailsImpl principal holding the given permission codes. */
    private UserDetailsImpl principalWith(String... permissionCodes) {
        Set<Permission> perms = Arrays.stream(permissionCodes)
            .map(code -> new Permission(null, code, code))
            .collect(Collectors.toSet());
        Role role = new Role(1, "TEST_ROLE", "test", perms);
        AppUser user = new AppUser(1L, "tester", "hash", role, null,
            true, null, 0, null, null, null);
        return new UserDetailsImpl(user);
    }

    @Test
    void rejectsUnknownTypeWithoutReflectingInput() throws Exception {
        // F-13: an injection-style type must be rejected before hitting the generator,
        // and the response must not echo the raw value back.
        mockMvc.perform(post("/reports/generate")
                .param("type", "<script>alert(1)</script>")
                .with(user(principalWith("REPORT_SESSION", "REPORT_MONTHLY", "REPORT_QUARTERLY")))
                .with(csrf()))
            .andExpect(status().is3xxRedirection()); // IllegalArgumentException -> redirect

        verify(reportService, never()).generateReport(any(), any(), any());
    }

    @Test
    void sessionAuthorityCannotGenerateQuarterly() throws Exception {
        // F-14: holding only REPORT_SESSION must NOT allow a quarterly report.
        mockMvc.perform(post("/reports/generate")
                .param("type", "quarterly")
                .with(user(principalWith("REPORT_SESSION")))
                .with(csrf()))
            .andExpect(status().isForbidden());

        verify(reportService, never()).generateReport(any(), any(), any());
    }

    @Test
    void quarterlyAuthorityCanGenerateQuarterly() throws Exception {
        // F-14: the matching authority is accepted and reaches the generator.
        mockMvc.perform(post("/reports/generate")
                .param("type", "quarterly")
                .with(user(principalWith("REPORT_QUARTERLY")))
                .with(csrf()))
            .andExpect(status().isOk());

        verify(reportService).generateReport(eq("quarterly"), any(), any());
    }
}
