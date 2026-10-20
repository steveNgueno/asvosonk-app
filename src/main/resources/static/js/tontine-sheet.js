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
     · 0 est un échec de cotisation ; une case vide n'est pas enregistrée ;
     · un échec passé se recouvre d'abord (montant imposé, ou multiple de
       5 000) : sans cela, la cotisation du jour est refusée.
   ============================================================= */
(function () {
  'use strict';

  var STEP = 5000;

  function money(value) {
    return value.toLocaleString('fr-FR');
  }

  function rows() {
    return Array.prototype.slice.call(document.querySelectorAll('#tontineSheet tr.js-tontine-row'))
      .filter(function (row) {
        return row.querySelector('.js-tontine-amount') || row.querySelector('.js-tontine-recovery');
      });
  }

  function recoveries(row) {
    return Array.prototype.slice.call(row.querySelectorAll('.js-tontine-recovery'));
  }

  function imposedOf(element) {
    return parseInt(element.getAttribute('data-imposed'), 10) || 0;
  }

  function setPill(pill, label, variant) {
    pill.textContent = label;
    pill.className = 'pill ' + variant + ' js-tontine-status';
  }

  /** Contrôle d'un recouvrement : renvoie un message d'erreur, ou null. */
  function recoveryError(input) {
    var raw = input.value.trim();
    if (raw === '') return null;
    var value = parseInt(raw, 10);
    var imposed = imposedOf(input);
    if (isNaN(value) || value < 0) return 'Recouvrement invalide';
    if (value === 0) return null;
    if (imposed > 0 && value !== imposed) return 'Recouvrement : ' + money(imposed);
    if (imposed === 0 && value % STEP !== 0) return 'Recouvrement : multiple de 5 000';
    return null;
  }

  function describe(row) {
    var pill = row.querySelector('.js-tontine-status');
    var input = row.querySelector('.js-tontine-amount');
    var recoveryInputs = recoveries(row);

    var recovered = 0;
    var missingRecovery = false;
    var error = null;
    recoveryInputs.forEach(function (r) {
      var value = parseInt(r.value.trim(), 10);
      if (r.value.trim() === '' || value === 0) {
        missingRecovery = true;
      } else if (!isNaN(value) && value > 0) {
        recovered += value;
      }
      error = error || recoveryError(r);
    });

    var raw = input ? input.value.trim() : '';
    var value = raw === '' ? null : parseInt(raw, 10);
    var total = recovered + (value !== null && !isNaN(value) && value > 0 ? value : 0);
    if (!pill) return total;

    if (error) { setPill(pill, error, 'pill-danger'); return total; }
    if (value === null) {
      if (recovered > 0) setPill(pill, 'Échec recouvert', 'pill-success');
      else setPill(pill, 'Non saisi', 'pill-muted');
      return total;
    }

    var imposed = input ? imposedOf(row) : 0;
    if (isNaN(value) || value < 0) {
      setPill(pill, 'Montant invalide', 'pill-danger');
    } else if (value === 0) {
      setPill(pill, 'Échec de cotisation', 'pill-danger');
    } else if (missingRecovery) {
      setPill(pill, 'Recouvrez d\'abord l\'échec', 'pill-danger');
    } else if (imposed > 0 && value !== imposed) {
      setPill(pill, 'Doit être ' + money(imposed), 'pill-danger');
    } else if (imposed > 0) {
      setPill(pill, 'Dette remboursée', 'pill-success');
    } else if (value < STEP) {
      setPill(pill, 'Minimum 5 000', 'pill-danger');
    } else if (value % STEP !== 0) {
      setPill(pill, 'Multiple de 5 000', 'pill-danger');
    } else {
      setPill(pill, recovered > 0 ? 'Échec recouvert + cotisation' : 'Cotisation', 'pill-success');
    }
    return total;
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
    // et propose le minimum aux autres, à ajuster. Les échecs à recouvrir
    // passent en premier : ils conditionnent la cotisation du jour.
    var fill = document.querySelector('[data-tontine-fill]');
    if (fill) {
      fill.addEventListener('click', function () {
        rows().forEach(function (row) {
          recoveries(row).forEach(function (r) {
            if (r.value.trim() !== '') return;
            var imposedRecovery = imposedOf(r);
            r.value = imposedRecovery > 0 ? imposedRecovery : STEP;
          });
          var input = row.querySelector('.js-tontine-amount');
          if (!input || input.value.trim() !== '') return;
          var imposed = imposedOf(row);
          input.value = imposed > 0 ? imposed : STEP;
        });
        recalc();
      });
    }

    var clear = document.querySelector('[data-tontine-clear]');
    if (clear) {
      clear.addEventListener('click', function () {
        rows().forEach(function (row) {
          var input = row.querySelector('.js-tontine-amount');
          if (input) input.value = '';
          recoveries(row).forEach(function (r) { r.value = ''; });
        });
        recalc();
      });
    }

    recalc();
  });
})();
