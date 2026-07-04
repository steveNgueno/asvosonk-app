package org.asvosonk.security.presentation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    /**
     * GET /login — shows the login form.
     * Spring Security handles POST /login automatically.
     */
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";   // → templates/auth/login.html
    }
}
