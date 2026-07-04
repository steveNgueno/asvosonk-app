package org.asvosonk.report.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Service that bridges Java to the Python report generator.
 * Calls the compiled asvosonk_reports.exe (or .py) via ProcessBuilder
 * and returns the generated PDF file path.
 *
 * The Python script prints the absolute path of the generated PDF to stdout;
 * this service reads it to know where the file was created.
 */
@Slf4j
@Service
public class ReportService {

    @Value("${asvosonk.reports.exe-path}")
    private String exePath;

    @Value("${asvosonk.reports.output-dir}")
    private String outputDir;

    private static final long TIMEOUT_SECONDS = 60;

    /**
     * Generate a PDF report by calling the Python executable.
     *
     * @param type report type: "session", "monthly", or "quarterly"
     * @param from start date
     * @param to   end date
     * @return Path to the generated PDF file
     * @throws IOException          if the process fails to start
     * @throws InterruptedException if the process is interrupted
     * @throws IllegalStateException if the report generation fails
     */
    public Path generateReport(String type, LocalDate from, LocalDate to)
            throws IOException, InterruptedException {

        // Ensure output directory exists
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);

        // Build command: [exe-path, type, from, to, output-dir]
        ProcessBuilder pb = new ProcessBuilder(
            exePath,
            type,
            from.format(DateTimeFormatter.ISO_LOCAL_DATE),
            to.format(DateTimeFormatter.ISO_LOCAL_DATE),
            outputDirPath.toAbsolutePath().toString()
        );

        pb.redirectErrorStream(false);  // keep stdout and stderr separate

        log.info("Generating {} report from {} to {} — command: {}",
            type, from, to, String.join(" ", pb.command()));

        Process process = pb.start();

        // Read stdout to get the generated file path (printed by Python)
        String stdout = new String(process.getInputStream().readAllBytes());

        // Wait for completion with timeout
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(
                "Le processus de génération de rapport a dépassé le délai de "
                    + TIMEOUT_SECONDS + " secondes.");
        }

        int exitCode = process.exitValue();

        if (exitCode != 0) {
            // Read stderr on failure
            String stderr = new String(process.getErrorStream().readAllBytes());
            log.error("Report generation failed (exit={}): {}", exitCode, stderr);
            throw new IllegalStateException(
                "La génération du rapport a échoué (code " + exitCode + ").\n" + stderr);
        }

        // The Python script prints the output path as the last line on stdout
        String trimmed = stdout.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException(
                "Le générateur de rapport n'a pas fourni le chemin du fichier PDF.");
        }

        // Parse the last line as the output path
        String[] lines = trimmed.split("\n");
        String lastLine = lines[lines.length - 1].trim();
        Path pdfPath = Paths.get(lastLine);

        if (!Files.exists(pdfPath)) {
            throw new IllegalStateException(
                "Le fichier PDF indiqué n'existe pas : " + pdfPath);
        }

        log.info("Report generated successfully: {}", pdfPath);
        return pdfPath;
    }
}
