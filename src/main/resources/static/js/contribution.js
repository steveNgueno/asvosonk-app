/* =============================================================
   ASVOSONK — saisie d'une cotisation de Grande Tontine
   Boutons de saisie rapide + rappel des règles de montant.
   Les règles sont validées côté serveur ; ce script n'en applique
   aucune, il se contente de guider la saisie.
   ============================================================= */
(function () {
  'use strict';

  var IMPOSED = 5000;

  document.addEventListener('DOMContentLoaded', function () {
    var amount = document.getElementById('contributionAmount');
    var hint = document.getElementById('amountHint');
    if (!amount) return;

    function refreshHint() {
      if (!hint) return;
      var value = parseInt(amount.value, 10);
      if (isNaN(value)) {
        hint.textContent = '';
      } else if (value === 0) {
        hint.textContent = 'Échec de cotisation : une sanction et une dette seront enregistrées par le serveur.';
      } else if (value < IMPOSED) {
        hint.textContent = 'La cotisation minimale est de 5 000 FCFA.';
      } else if (value % IMPOSED !== 0) {
        hint.textContent = 'Le montant doit être un multiple de 5 000 FCFA.';
      } else {
        hint.textContent = 'Cotisation de ' + value.toLocaleString('fr-FR') + ' FCFA.';
      }
    }

    document.querySelectorAll('[data-amount]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        amount.value = btn.getAttribute('data-amount');
        refreshHint();
        amount.focus();
      });
    });

    document.querySelectorAll('[data-amount-add]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var step = parseInt(btn.getAttribute('data-amount-add'), 10) || 0;
        amount.value = (parseInt(amount.value, 10) || 0) + step;
        refreshHint();
        amount.focus();
      });
    });

    amount.addEventListener('input', refreshHint);
    refreshHint();
  });
})();
