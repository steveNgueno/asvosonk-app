package org.asvosonk.web.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the current request URI to every Thymeleaf view as {@code currentUri}.
 * <p>
 * Thymeleaf 3.1 (Spring Boot 3.x) removed the {@code #request} expression object,
 * so templates can no longer read {@code #request.requestURI} directly. This advice
 * restores that capability in a supported way, used for the active-navbar-link
 * highlight (UI-003).
 */
@ControllerAdvice
public class CurrentUriAdvice {

    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
