package org.asvosonk.report.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.report.application.service.ReportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;

@Slf4j
@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * F-13/F-14: whitelist of accepted report types. Any value outside this
     * enum is rejected before reaching the report generator, and each type
     * carries the authority the caller must hold to generate it.
     */
    private enum ReportType {
        session("REPORT_SESSION"),
        monthly("REPORT_MONTHLY"),
        quarterly("REPORT_QUARTERLY");

        final String authority;
        ReportType(String authority) { this.authority = authority; }

        static ReportType fromParam(String raw) {
            return Arrays.stream(values())
                .filter(t -> t.name().equals(raw))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type de rapport inconnu"));
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_SESSION')")
    public String index(Model model) {
        model.addAttribute("pageTitle", "Rapports");
        return "reports/index";
    }

    @PostMapping("/generate")
    public ResponseEntity<Resource> generate(@RequestParam String type,
                                             @RequestParam(required = false) LocalDate dateFrom,
                                             @RequestParam(required = false) LocalDate dateTo,
                                             Authentication authentication) {
        // F-13: validate the type against the whitelist BEFORE doing anything.
        ReportType reportType = ReportType.fromParam(type);

        // F-14: enforce the authority matching the requested type, not a single
        // blanket REPORT_SESSION for all of them.
        if (!hasAuthority(authentication, reportType.authority)) {
            throw new AccessDeniedException(
                "Permission requise pour ce type de rapport : " + reportType.authority);
        }

        LocalDate from = dateFrom != null ? dateFrom : LocalDate.now();
        LocalDate to = dateTo != null ? dateTo : from;

        try {
            Path pdfPath = reportService.generateReport(reportType.name(), from, to);
            Resource resource = new FileSystemResource(pdfPath.toFile());
            if (!resource.exists()) {
                throw new IllegalStateException("Fichier PDF généré introuvable : " + pdfPath);
            }

            String filename = "rapport_asvosonk_" + LocalDate.now() + ".pdf";
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(resource);

        } catch (ReportGenerationException e) {
            throw e;
        } catch (Exception e) {
            // F-13: never reflect e.getMessage() into an HTML response (XSS +
            // info leak). Log the detail server-side, surface a safe message.
            log.error("Échec de génération du rapport type={} from={} to={}", type, from, to, e);
            throw new ReportGenerationException();
        }
    }

    private boolean hasAuthority(Authentication auth, String authority) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(authority::equals);
    }

    /** Thrown when report generation fails; carries a safe, user-facing message. */
    static class ReportGenerationException extends RuntimeException {
        ReportGenerationException() {
            super("La génération du rapport a échoué. Réessayez ou contactez l'administrateur.");
        }
    }
}
