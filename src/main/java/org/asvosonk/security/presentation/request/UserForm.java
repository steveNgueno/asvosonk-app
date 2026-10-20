package org.asvosonk.security.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter @Setter
@NoArgsConstructor
public class UserForm {

    @NotBlank(message = "Le login est obligatoire")
    @Pattern(regexp = "[A-Za-z0-9._-]{3,50}",
             message = "Le login doit faire de 3 à 50 caractères : lettres sans accent, chiffres, « . », « _ » ou « - ».")
    private String login;

    @NotBlank(message = "Le rôle est obligatoire")
    private String roleName;

    private Long memberId;

    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    private String confirmPassword;

    /**
     * Espaces de bord retirés avant validation : un login enregistré avec ses
     * espaces (« ␣␣Tresorier␣ ») ne pouvait plus servir à se connecter.
     */
    public void setLogin(String login) {
        this.login = login == null ? null : login.trim();
    }

    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }
}
