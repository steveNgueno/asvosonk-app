/* Écran de connexion : bascule d'affichage du mot de passe. */
(function () {
  'use strict';
  document.addEventListener('DOMContentLoaded', function () {
    var toggle = document.getElementById('togglePassword');
    var input = document.getElementById('password');
    var icon = document.getElementById('eyeIcon');
    if (!toggle || !input || !icon) return;

    toggle.addEventListener('click', function () {
      var show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      icon.className = show ? 'bi bi-eye-slash' : 'bi bi-eye';
      toggle.setAttribute('aria-pressed', show ? 'true' : 'false');
      toggle.setAttribute('aria-label', show ? 'Masquer le mot de passe' : 'Afficher le mot de passe');
      input.focus();
    });
  });
})();
