/* =============================================================
   ASVOSONK — création d'un tour (présence ou grande tontine)
   -------------------------------------------------------------
   Deux listes sont envoyées au serveur : les membres cochés
   (participantIds) et leur ordre de passage (drawOrders). Les
   champs d'ordre désactivés ne sont pas envoyés : cocher active le
   champ de la ligne, si bien que les deux listes restent alignées
   ligne par ligne, dans le même ordre.

   L'ordre proposé automatiquement n'est qu'une aide à la saisie :
   l'unicité et la complétude sont validées par le serveur.
   ============================================================= */
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    var table = document.getElementById('participantsTable');
    if (!table) return;

    var counter = document.getElementById('participantCount');

    /* Seules les lignes de membre : une ligne « aucune donnée » ou une ligne
       de séparation n'a pas de case à cocher et ferait échouer le reste. */
    function rows() {
      return Array.prototype.slice.call(table.querySelectorAll('tbody tr'))
        .filter(function (row) {
          return row.querySelector('.js-participant') && row.querySelector('.js-order');
        });
    }

    function orderInputOf(row) { return row.querySelector('.js-order'); }
    function checkboxOf(row) { return row.querySelector('.js-participant'); }

    function usedOrders() {
      return rows()
        .filter(function (r) { return checkboxOf(r).checked; })
        .map(function (r) { return parseInt(orderInputOf(r).value, 10); })
        .filter(function (n) { return !isNaN(n); });
    }

    function nextFreeOrder() {
      var used = usedOrders();
      var candidate = 1;
      while (used.indexOf(candidate) !== -1) candidate++;
      return candidate;
    }

    function refresh() {
      var selected = 0;
      var seen = {};
      var duplicate = false;

      rows().forEach(function (row) {
        var cb = checkboxOf(row);
        var order = orderInputOf(row);
        order.disabled = !cb.checked;
        order.required = cb.checked;
        row.classList.toggle('row-beneficiary', false);
        if (!cb.checked) {
          order.value = '';
          order.classList.remove('is-invalid');
          return;
        }
        selected++;
        var value = order.value;
        if (value && seen[value]) {
          duplicate = true;
          order.classList.add('is-invalid');
        } else {
          order.classList.remove('is-invalid');
          if (value) seen[value] = true;
        }
      });

      if (counter) counter.textContent = selected;
      var warning = document.getElementById('orderWarning');
      if (warning) warning.hidden = !duplicate;
      var submit = document.getElementById('tourSubmit');
      if (submit) submit.disabled = duplicate || selected < 2;
    }

    rows().forEach(function (row) {
      var cb = checkboxOf(row);
      var order = orderInputOf(row);
      cb.addEventListener('change', function () {
        if (cb.checked && !order.value) order.value = nextFreeOrder();
        refresh();
      });
      order.addEventListener('input', refresh);
    });

    var selectAll = document.getElementById('selectAllParticipants');
    if (selectAll) {
      selectAll.addEventListener('click', function () {
        rows().forEach(function (row, index) {
          var cb = checkboxOf(row);
          cb.checked = true;
          orderInputOf(row).value = index + 1;
        });
        refresh();
      });
    }
    var clearAll = document.getElementById('clearAllParticipants');
    if (clearAll) {
      clearAll.addEventListener('click', function () {
        rows().forEach(function (row) { checkboxOf(row).checked = false; });
        refresh();
      });
    }

    refresh();
  });
})();
