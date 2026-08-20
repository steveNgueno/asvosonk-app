/* =============================================================
   ASVOSONK — feuille de cotisation de la grande tontine
   -------------------------------------------------------------
   Aide à la saisie, sans rien décider : les règles de montant sont
   validées par le serveur à l'enregistrement.

   Rappel des règles annoncées ici :
     · un membre déjà bénéficiaire rend exactement ce que le bénéficiaire
       du jour lui avait cotisé (« montant imposé ») ;
     · les autres donnent librement, au minimum 5 000 FCFA et par
       multiples de 5 000 ;
     · 0 est un échec de cotisation ; une case vide n'est pas enregistrée.
   ============================================================= */
(function () {
  'use strict';

  var STEP = 5000;

  function money(value) {
    return value.toLocaleString('fr-FR');
  }

  function rows() {
    return Array.prototype.slice.call(document.querySelectorAll('#tontineSheet tr.js-tontine-row'))
      .filter(function (row) { return row.querySelector('.js-tontine-amount'); });
  }

  function imposedOf(row) {
    return parseInt(row.getAttribute('data-imposed'), 10) || 0;
  }

  function describe(row) {
    var input = row.querySelector('.js-tontine-amount');
    var pill = row.querySelector('.js-tontine-status');
    if (!pill) return 0;

    var raw = input.value.trim();
    if (raw === '') {
      pill.textContent = 'Non saisi';
      pill.className = 'pill pill-muted js-tontine-status';
      return 0;
    }

    var value = parseInt(raw, 10);
    var imposed = imposedOf(row);
    var label, variant;

    if (isNaN(value) || value < 0) {
      label = 'Montant invalide'; variant = 'pill-danger';
      value = 0;
    } else if (value === 0) {
      label = 'Échec de cotisation'; variant = 'pill-danger';
    } else if (imposed > 0 && value !== imposed) {
      label = 'Doit être ' + money(imposed); variant = 'pill-danger';
    } else if (imposed > 0) {
      label = 'Dette remboursée'; variant = 'pill-success';
    } else if (value < STEP) {
      label = 'Minimum 5 000'; variant = 'pill-danger';
    } else if (value % STEP !== 0) {
      label = 'Multiple de 5 000'; variant = 'pill-danger';
    } else {
      label = 'Cotisation'; variant = 'pill-success';
    }

    pill.textContent = label;
    pill.className = 'pill ' + variant + ' js-tontine-status';
    return value;
  }

  function recalc() {
    var total = 0;
    rows().forEach(function (row) { total += describe(row); });
    var out = document.getElementById('tontineSheetTotal');
    if (out) out.textContent = money(total);
  }

  document.addEventListener('DOMContentLoaded', function () {
    var table = document.getElementById('tontineSheet');
    if (!table) return;

    table.addEventListener('input', recalc);
    table.addEventListener('change', recalc);

    // Pré-remplit les montants imposés — les seuls qui ne se discutent pas —
    // et propose le minimum aux autres, à ajuster.
    var fill = document.querySelector('[data-tontine-fill]');
    if (fill) {
      fill.addEventListener('click', function () {
        rows().forEach(function (row) {
          var input = row.querySelector('.js-tontine-amount');
          if (input.value.trim() !== '') return;
          var imposed = imposedOf(row);
          input.value = imposed > 0 ? imposed : STEP;
        });
        recalc();
      });
    }

    var clear = document.querySelector('[data-tontine-clear]');
    if (clear) {
      clear.addEventListener('click', function () {
        rows().forEach(function (row) { row.querySelector('.js-tontine-amount').value = ''; });
        recalc();
      });
    }

    recalc();
  });
})();
