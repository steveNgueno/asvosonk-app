package org.asvosonk.web.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal UserDetailsImpl principal,
                            Model model) {
        if (principal == null || principal.getAppUser() == null) {
            return "redirect:/login";
        }
        model.addAttribute("userName",  principal.getAppUser().getLogin());
        model.addAttribute("roleName",  principal.getAppUser().getRole().getName());
        model.addAttribute("lastLogin", principal.getAppUser().getLastLogin());
        return "dashboard";   // → templates/dashboard.html
    }
}
