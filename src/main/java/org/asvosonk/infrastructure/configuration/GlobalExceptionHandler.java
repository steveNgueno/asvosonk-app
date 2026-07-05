package org.asvosonk.infrastructure.configuration;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.DuplicateResourceException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Central exception handler for the ASVOSONK application.
 * Maps domain exceptions to user-friendly error pages or redirects with flash messages.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // ── 404: Resource not found ──────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        log.warn("Resource not found: {}", ex.getMessage());
        model.addAttribute("pageTitle", "404 — Introuvable");
        model.addAttribute("errorDetail", ex.getMessage());
        return "error/404";
    }

    // ── Silently ignore missing static resources ─────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Static resource not found (ignored): {}", ex.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    // ── 403: Access denied ───────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        log.warn("Access denied: {}", ex.getMessage());
        model.addAttribute("pageTitle", "403 — Accès refusé");
        model.addAttribute("errorDetail",
            "Vous n'avez pas les permissions nécessaires pour accéder à cette ressource.");
        return "error/403";
    }

    // ── Business rule violations (redirect with flash) ───────

    @ExceptionHandler(BusinessRuleException.class)
    public String handleBusinessRule(BusinessRuleException ex,
                                     HttpServletRequest request,
                                     RedirectAttributes ra) {
        log.warn("Business rule violation: {}", ex.getMessage());
        ra.addFlashAttribute("errorMessage", ex.getMessage());

        // Redirect back to the referrer if available, otherwise to the dashboard
        String referer = Optional.ofNullable(request.getHeader("Referer"))
            .orElse("/dashboard");
        return "redirect:" + referer;
    }

    // ── Duplicate resource (redirect with flash) ─────────────

    @ExceptionHandler(DuplicateResourceException.class)
    public String handleDuplicateResource(DuplicateResourceException ex,
                                          HttpServletRequest request,
                                          RedirectAttributes ra) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        ra.addFlashAttribute("errorMessage", ex.getMessage());

        String referer = Optional.ofNullable(request.getHeader("Referer"))
            .orElse("/dashboard");
        return "redirect:" + referer;
    }

    // ── Illegal argument / illegal state (redirect with flash) ──

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleIllegalArgument(RuntimeException ex,
                                        HttpServletRequest request,
                                        RedirectAttributes ra) {
        log.warn("Illegal argument/state: {}", ex.getMessage());
        ra.addFlashAttribute("errorMessage", ex.getMessage());

        String referer = Optional.ofNullable(request.getHeader("Referer"))
            .orElse("/dashboard");
        return "redirect:" + referer;
    }

    // ── 500: Unexpected errors ───────────────────────────────

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpectedError(Exception ex, Model model, HttpServletRequest request) {
        log.error("Unexpected error: {} at {}", ex.getMessage(), request.getRequestURI(), ex);
        model.addAttribute("pageTitle", "500 — Erreur interne");
        model.addAttribute("errorDetail",
            "Une erreur inattendue s'est produite. Veuillez réessayer ou contacter l'administrateur.");
        return "error/500";
    }
}
