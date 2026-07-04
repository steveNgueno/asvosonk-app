package org.asvosonk.security.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter @Setter
@NoArgsConstructor
public class UserForm {

    @NotBlank(message = "Le login est obligatoire")
    @Size(max = 50, message = "Le login ne doit pas dépasser 50 caractères")
    private String login;

    @NotBlank(message = "Le rôle est obligatoire")
    private String roleName;

    private Long memberId;

    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    private String confirmPassword;

    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }
}
