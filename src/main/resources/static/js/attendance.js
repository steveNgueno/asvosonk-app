/* =============================================================
   ASVOSONK — feuille de présence
   -------------------------------------------------------------
   Aide à la saisie : pour chaque montant tapé, l'écran annonce l'effet
   attendu et totalise ce qui alimentera la tontine et les caisses.

   Le calcul rejoue l'ordre d'imputation de la clôture, tel que décrit
   dans RevolvingFundService :

     1. l'espèce solde les dettes antérieures, de la plus ancienne à la
        plus récente, et s'arrête à la première qu'elle ne couvre pas ;
        rembourser une avance recharge le fond de roulement ;
     2. le fond reprend ensuite les échecs restants tant qu'il en a les
        moyens ;
     3. la cotisation du jour est réglée en espèces, à défaut complétée
        par le fond, sinon c'est l'échec ;
     4. le bénéficiaire du jour, s'il n'est pas à jour, est régularisé
        d'office sur la tontine qu'il s'apprête à percevoir.

   Restent au serveur, et ne sont donc pas chiffrés ici : les sanctions
   et les retenues sur la tontine. L'estimation est une prévision, pas
   une vérité comptable — la clôture reste seule juge.
   ============================================================= */
(function () {
  'use strict';

  var BEVERAGE_SHARE = 500;
  var DEVELOPMENT_SHARE = 500;
  var BEVERAGE_COST_PER_PERSON = 500;

  function money(value) {
    return value.toLocaleString('fr-FR');
  }

  function intAttr(row, name) {
    return parseInt(row.getAttribute(name), 10) || 0;
  }

  /* Lignes de membre uniquement : une ligne « aucune donnée » n'a pas de champ. */
  function memberRows() {
    return Array.prototype.slice.call(
      document.querySelectorAll('#attendanceTable tr.js-row')
    ).filter(function (row) {
      return row.querySelector('.js-present') && row.querySelector('.js-amount');
    });
  }

  /* « a2000,f1000 » → [{amount: 2000, advance: true}, {amount: 1000, advance: false}] */
  function debtsOf(row) {
    return (row.getAttribute('data-debts') || '')
      .split(',')
      .filter(Boolean)
      .map(function (token) {
        return { advance: token.charAt(0) === 'a', amount: parseInt(token.slice(1), 10) || 0 };
      });
  }

  /* Montant à réclamer pour solder toutes les dettes puis la séance. */
  function suggestion(row) {
    var total = intAttr(row, 'data-due');
    debtsOf(row).forEach(function (debt) { total += debt.amount; });
    return total === intAttr(row, 'data-due') ? 0 : total;
  }

  /**
   * Rejoue la clôture pour une ligne.
   * @param purse tontine mobilisable — non nulle pour le seul bénéficiaire.
   */
  function settle(row, purse) {
    var due = intAttr(row, 'data-due');
    var fund = intAttr(row, 'data-fund');
    var debts = debtsOf(row);
    var cash = parseFloat(row.querySelector('.js-amount').value) || 0;

    var result = {
      status: 'default', fromTontine: 0, settledDebts: 0, recoveries: 0,
      tontine: 0, development: 0, beverage: 0
    };

    // 4. Régularisation obligatoire du bénéficiaire, prise sur sa tontine.
    if (purse > 0) {
      var owed = due;
      debts.forEach(function (debt) { owed += debt.amount; });
      var drawn = Math.min(Math.max(0, owed - cash), purse);
      if (drawn > 0) {
        cash += drawn;
        result.fromTontine = drawn;
      }
    }

    // 1. L'espèce solde les dettes les plus anciennes.
    var i;
    for (i = 0; i < debts.length; i++) {
      if (cash < debts[i].amount) break;
      cash -= debts[i].amount;
      debts[i].settled = true;
      result.settledDebts++;
      if (debts[i].advance) {
        fund += debts[i].amount;          // l'argent retourne dans le fond
      } else {
        result.recoveries++;
        result.development += DEVELOPMENT_SHARE;
        result.beverage += BEVERAGE_SHARE;
      }
    }

    // 2. Le fond reprend les échecs restants tant qu'il le peut.
    for (i = 0; i < debts.length; i++) {
      if (debts[i].advance || debts[i].settled) continue;
      if (fund < debts[i].amount) break;
      fund -= debts[i].amount;
      debts[i].settled = true;
      result.settledDebts++;
      result.recoveries++;
      result.development += DEVELOPMENT_SHARE;
      result.beverage += BEVERAGE_SHARE;
    }

    // 3. Cotisation du jour.
    var fromCash = Math.min(cash, due);
    var shortfall = due - fromCash;
    if (shortfall === 0) {
      result.status = result.fromTontine > 0 ? 'tontine' : 'cash';
    } else if (fund >= shortfall) {
      result.status = fromCash > 0 ? 'compromise' : 'fund';
    } else {
      result.status = 'failure';
    }

    if (result.status !== 'failure') {
      result.tontine = Math.max(0, due - 1000);   // 1 000 boisson + développement
      result.development += DEVELOPMENT_SHARE;
      result.beverage += BEVERAGE_SHARE;
    }
    return result;
  }

  var LABELS = {
    cash:       ['Cotisation complète',      'pill-success'],
    tontine:    ['Régularisé sur sa tontine', 'pill-accent'],
    compromise: ['Complément du fond',       'pill-warning'],
    fund:       ['Cotisé par le fond',       'pill-warning'],
    failure:    ['Échec de cotisation',      'pill-danger']
  };

  function render(row, result) {
    var pill = row.querySelector('.js-status');
    var note = row.querySelector('.js-effect-note');
    var label = LABELS[result.status] || LABELS.failure;
    pill.textContent = label[0];
    pill.className = 'pill js-status ' + label[1];

    if (!note) return;
    var parts = [];
    if (result.settledDebts > 0) {
      parts.push(result.settledDebts + (result.settledDebts > 1 ? ' dettes soldées' : ' dette soldée'));
    }
    if (result.fromTontine > 0) {
      parts.push(money(result.fromTontine) + ' pris sur sa tontine');
    }
    note.textContent = parts.join(' · ');
  }

  function recalc() {
    var rows = memberRows();
    var present = 0, tontine = 0, dev = 0, beveragePool = 0;
    var beneficiary = null;

    rows.forEach(function (row) {
      if (row.querySelector('.js-present').checked) present++;
      // Le bénéficiaire est traité en dernier : sa régularisation se prend sur
      // la tontine des autres, qui doit donc être connue avant.
      if (row.getAttribute('data-beneficiary') === 'true') {
        beneficiary = row;
        return;
      }
      var result = settle(row, 0);
      tontine += result.tontine;
      dev += result.development;
      beveragePool += result.beverage;
      render(row, result);
    });

    if (beneficiary) {
      var last = settle(beneficiary, tontine);
      tontine += last.tontine;
      dev += last.development;
      beveragePool += last.beverage;
      render(beneficiary, last);
      tontine = Math.max(0, tontine - last.fromTontine);
    }

    var reliquat = Math.max(0, beveragePool - present * BEVERAGE_COST_PER_PERSON);

    document.getElementById('sumPresent').textContent = present;
    document.getElementById('sumTontine').textContent = money(tontine);
    document.getElementById('sumDev').textContent = money(dev);
    document.getElementById('sumBeverage').textContent = money(reliquat);
  }

  document.addEventListener('DOMContentLoaded', function () {
    var table = document.getElementById('attendanceTable');
    if (!table) return;

    // Montant conseillé pour tout solder, affiché une fois pour toutes.
    memberRows().forEach(function (row) {
      var hint = row.querySelector('.js-suggestion');
      var total = suggestion(row);
      if (hint && total > 0) {
        hint.textContent = ' · pour tout solder : ' + money(total) + ' FCFA';
      }
    });

    var dirty = false;
    function markDirty() { dirty = true; }

    table.addEventListener('input', function () { recalc(); markDirty(); });
    table.addEventListener('change', function () { recalc(); markDirty(); });

    document.querySelectorAll('[data-mark-all]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var value = btn.getAttribute('data-mark-all') === 'true';
        memberRows().forEach(function (row) {
          row.querySelector('.js-present').checked = value;
        });
        recalc();
        markDirty();
      });
    });

    var form = document.getElementById('attendanceForm');
    if (form) form.addEventListener('submit', function () { dirty = false; });
    window.addEventListener('beforeunload', function (e) {
      if (!dirty) return;
      e.preventDefault();
      e.returnValue = '';
    });

    recalc();
  });
})();
