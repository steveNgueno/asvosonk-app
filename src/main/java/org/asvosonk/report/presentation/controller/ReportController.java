package org.asvosonk.report.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.asvosonk.report.application.service.ReportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.nio.file.Path;
import java.time.LocalDate;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_SESSION')")
    public String index(Model model) {
        model.addAttribute("pageTitle", "Rapports");
        return "reports/index";
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('REPORT_SESSION')")
    public ResponseEntity<?> generate(@RequestParam String type,
                                      @RequestParam(required = false) LocalDate dateFrom,
                                      @RequestParam(required = false) LocalDate dateTo) {
        try {
            // Set defaults for single-date reports (session)
            LocalDate from = dateFrom;
            LocalDate to = dateTo;
            if (from == null) from = LocalDate.now();
            if (to == null) to = from;

            Path pdfPath = reportService.generateReport(type, from, to);

            Resource resource = new FileSystemResource(pdfPath.toFile());

            if (!resource.exists()) {
                throw new IllegalStateException(
                    "Le fichier PDF généré est introuvable : " + pdfPath);
            }

            String filename = "rapport_asvosonk_" + LocalDate.now() + ".pdf";

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                    "attachment; filename=\"" + filename + "\"")
                .body(resource);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_HTML)
                .header("Content-Disposition", "inline")
                .body("<html><body><script>window.location.href='/reports';</script>"
                    + "<p>Erreur : " + e.getMessage() + "</p></body></html>");
        }
    }
}
