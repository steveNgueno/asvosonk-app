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

    private static final String FALLBACK_REDIRECT = "/dashboard";

    // ── 404: Resource not found ──────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        log.warn("Resource not found: {}", ex.getMessage());
        model.addAttribute("pageTitle", "404 — Introuvable");
        model.addAttribute("errorDetail", ex.getMessage());
        return "error/404";
    }

    // ── Unknown URL ──────────────────────────────────────────

    /**
     * Spring Boot raises {@code NoResourceFoundException} for <em>any</em> path
     * that matches no controller, not only for missing files. Returning an empty
     * 404 for all of them left a user who mistyped a URL on a blank page; only
     * asset-looking requests (a file extension, or a client that does not ask for
     * HTML) get the silent empty response now.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(NoResourceFoundException ex,
                                        HttpServletRequest request,
                                        Model model) {
        String path = ex.getResourcePath() != null ? ex.getResourcePath() : "";
        String accept = Optional.ofNullable(request.getHeader("Accept")).orElse("");
        boolean looksLikeAsset = path.contains(".");
        boolean wantsHtml = accept.contains("text/html") || accept.contains("*/*");

        if (looksLikeAsset || !wantsHtml) {
            log.debug("Resource not found (empty 404): {}", path);
            return ResponseEntity.notFound().build();
        }

        log.warn("Unknown page requested: {}", request.getRequestURI());
        model.addAttribute("pageTitle", "404 — Introuvable");
        return new org.springframework.web.servlet.ModelAndView(
            "error/404", model.asMap(), HttpStatus.NOT_FOUND);
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
        return backToCaller(request);
    }

    // ── Duplicate resource (redirect with flash) ─────────────

    @ExceptionHandler(DuplicateResourceException.class)
    public String handleDuplicateResource(DuplicateResourceException ex,
                                          HttpServletRequest request,
                                          RedirectAttributes ra) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        ra.addFlashAttribute("errorMessage", ex.getMessage());
        return backToCaller(request);
    }

    // ── Illegal argument / illegal state (redirect with flash) ──

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleIllegalArgument(RuntimeException ex,
                                        HttpServletRequest request,
                                        RedirectAttributes ra) {
        log.warn("Illegal argument/state: {}", ex.getMessage());
        ra.addFlashAttribute("errorMessage", ex.getMessage());
        return backToCaller(request);
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

    // ── Helpers ──────────────────────────────────────────────

    /**
     * Sends the user back where they came from, after the flash message is set.
     *
     * <p>The {@code Referer} header is attacker-controllable, so it is never
     * concatenated into a redirect as-is: an absolute URL (or a scheme-relative
     * {@code //evil.example}) would turn any business-rule violation into an
     * open redirect (CWE-601). Only a same-origin path from this application is
     * accepted; anything else falls back to the dashboard.
     */
    private String backToCaller(HttpServletRequest request) {
        return "redirect:" + safeReferer(request);
    }

    private String safeReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return FALLBACK_REDIRECT;
        }

        String candidate = referer.trim();

        // Absolute URL: keep only its path+query, and only when the host matches ours.
        if (candidate.contains("://")) {
            try {
                java.net.URI uri = java.net.URI.create(candidate);
                if (!isSameHost(uri, request)) {
                    return FALLBACK_REDIRECT;
                }
                candidate = uri.getRawPath() == null || uri.getRawPath().isEmpty()
                    ? FALLBACK_REDIRECT
                    : uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
            } catch (IllegalArgumentException malformed) {
                return FALLBACK_REDIRECT;
            }
        }

        // Reject protocol-relative ("//host"), backslash tricks and anything not rooted.
        if (!candidate.startsWith("/") || candidate.startsWith("//") || candidate.contains("\\")
            || candidate.contains("\n") || candidate.contains("\r")) {
            return FALLBACK_REDIRECT;
        }
        // Never bounce a failed POST back to the login page.
        if (candidate.startsWith("/login")) {
            return FALLBACK_REDIRECT;
        }
        return candidate;
    }

    private boolean isSameHost(java.net.URI uri, HttpServletRequest request) {
        String host = uri.getHost();
        return host != null && host.equalsIgnoreCase(request.getServerName());
    }
}
