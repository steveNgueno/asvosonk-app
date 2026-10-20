package org.asvosonk.security.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.security.application.service.UserAdministrationPolicy;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.security.application.usecase.AssignRoleUseCase;
import org.asvosonk.security.application.usecase.CreateUserUseCase;
import org.asvosonk.security.application.usecase.LockUserUseCase;
import org.asvosonk.security.application.usecase.UpdatePasswordUseCase;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.security.domain.repository.RoleRepository;
import org.asvosonk.security.presentation.request.UserForm;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final CreateUserUseCase   createUserUseCase;
    private final AssignRoleUseCase   assignRoleUseCase;
    private final UpdatePasswordUseCase updatePasswordUseCase;
    private final LockUserUseCase     lockUserUseCase;
    private final AppUserRepository   appUserRepository;
    private final RoleRepository      roleRepository;
    private final SearchMemberUseCase searchMemberUseCase;
    private final UserAdministrationPolicy userAdministrationPolicy;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public String list(Model model) {
        model.addAttribute("users", appUserRepository.findAll());
        model.addAttribute("pageTitle", "Utilisateurs");
        return "users/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public String newForm(Model model) {
        model.addAttribute("form", new UserForm());
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("members", searchMemberUseCase.findAllActive());
        model.addAttribute("editMode", false);
        model.addAttribute("pageTitle", "Nouvel utilisateur");
        return "users/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public String create(@Valid @ModelAttribute("form") UserForm form,
                         BindingResult result,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         Model model,
                         RedirectAttributes ra) {
        if (!form.isPasswordMatching()) {
            result.rejectValue("confirmPassword", "error", "Les mots de passe ne correspondent pas");
        }
        if (result.hasErrors()) {
            model.addAttribute("roles", roleRepository.findAll());
            model.addAttribute("members", searchMemberUseCase.findAllActive());
            model.addAttribute("editMode", false);
            model.addAttribute("pageTitle", "Nouvel utilisateur");
            return "users/form";
        }

        try {
            userAdministrationPolicy.requireCanCreate(principal.getAppUser().getId(), form.getRoleName());
            AppUser user = createUserUseCase.createUser(
                form.getLogin(), form.getPassword(), form.getRoleName(), form.getMemberId());
            ra.addFlashAttribute("successMessage",
                "Utilisateur « " + user.getLogin() + " » créé avec succès.");
            return "redirect:/users";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("roles", roleRepository.findAll());
            model.addAttribute("members", searchMemberUseCase.findAllActive());
            model.addAttribute("editMode", false);
            model.addAttribute("pageTitle", "Nouvel utilisateur");
            return "users/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('USER_EDIT')")
    public String editForm(@PathVariable Long id, Model model) {
        AppUser user = appUserRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + id));

        UserForm form = new UserForm();
        form.setLogin(user.getLogin());
        form.setRoleName(user.getRole().getName());
        form.setMemberId(user.getMember() != null ? user.getMember().getId() : null);

        model.addAttribute("form", form);
        model.addAttribute("userId", id);
        model.addAttribute("userLogin", user.getLogin());
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("members", searchMemberUseCase.findAllActive());
        model.addAttribute("editMode", true);
        model.addAttribute("pageTitle", "Modifier — " + user.getLogin());
        return "users/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('USER_EDIT')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") UserForm form,
                         BindingResult result,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            // Re-render the form instead of redirecting: a redirect dropped the
            // BindingResult, so the user was bounced back to a pristine form with
            // no indication of what was wrong.
            AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + id));
            model.addAttribute("userId", id);
            model.addAttribute("userLogin", user.getLogin());
            model.addAttribute("roles", roleRepository.findAll());
            model.addAttribute("members", searchMemberUseCase.findAllActive());
            model.addAttribute("editMode", true);
            model.addAttribute("pageTitle", "Modifier — " + user.getLogin());
            return "users/form";
        }

        try {
            userAdministrationPolicy.requireCanAssignRole(
                principal.getAppUser().getId(), id, form.getRoleName());
            assignRoleUseCase.assignRole(id, form.getRoleName());
            ra.addFlashAttribute("successMessage", "Rôle mis à jour.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/password")
    @PreAuthorize("hasAuthority('USER_EDIT')")
    public String updatePassword(@PathVariable Long id,
                                 @RequestParam String password,
                                 @RequestParam String confirmPassword,
                                 @AuthenticationPrincipal UserDetailsImpl principal,
                                 RedirectAttributes ra) {
        // Seul le PRESIDENT, ou l'utilisateur lui-même, change un mot de passe.
        // Le contrôle porte sur le rôle : la permission USER_DEACTIVATE testée
        // auparavant est aussi détenue par SECRETARY et TREASURER.
        try {
            userAdministrationPolicy.requireCanChangePassword(principal.getAppUser().getId(), id);
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/users";
        }

        if (password == null || password.length() < 8) {
            ra.addFlashAttribute("errorMessage", "Le mot de passe doit contenir au moins 8 caractères");
            return "redirect:/users";
        }
        if (!password.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMessage", "Les mots de passe ne correspondent pas");
            return "redirect:/users";
        }

        try {
            updatePasswordUseCase.updatePassword(id, password);
            ra.addFlashAttribute("successMessage", "Mot de passe mis à jour.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_DEACTIVATE')")
    public String deactivate(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetailsImpl principal,
                             RedirectAttributes ra) {
        try {
            // Compte PRESIDENT : réservé à un PRESIDENT, jamais le dernier actif.
            userAdministrationPolicy.requireCanDeactivate(principal.getAppUser().getId(), id);
            lockUserUseCase.deactivateUser(id);
            ra.addFlashAttribute("successMessage", "Utilisateur désactivé.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/users";
    }
}
