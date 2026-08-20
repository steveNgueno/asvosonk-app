/* =============================================================
   ASVOSONK — comportements d'interface partagés
   Aucune dépendance en dehors du bundle Bootstrap déjà chargé.
   ============================================================= */
(function () {
  'use strict';

  /* ── Navigation latérale (mobile) ───────────────────────── */
  function initNavToggle() {
    var toggle = document.querySelector('[data-nav-toggle]');
    var backdrop = document.querySelector('.nav-backdrop');
    if (!toggle) return;

    function close() {
      document.body.classList.remove('nav-open');
      toggle.setAttribute('aria-expanded', 'false');
    }

    toggle.addEventListener('click', function () {
      var open = document.body.classList.toggle('nav-open');
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    if (backdrop) backdrop.addEventListener('click', close);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') close();
    });
  }

  /* ── Messages flash : fermeture automatique ─────────────
     Seuls les messages de succès disparaissent d'eux-mêmes ; une
     erreur reste affichée jusqu'à ce que l'utilisateur la ferme
     (elle demande souvent une action de sa part). */
  function initFlash() {
    var success = document.getElementById('flashSuccess');
    if (!success || !window.bootstrap) return;
    window.setTimeout(function () {
      var alert = window.bootstrap.Alert.getOrCreateInstance(success);
      if (alert) alert.close();
    }, 6000);
  }

  /* ── Confirmation avant une action sensible ──────────────
     <form data-confirm="Message ?"> — remplace les onsubmit inline. */
  function initConfirm() {
    document.querySelectorAll('[data-confirm]').forEach(function (el) {
      el.addEventListener('submit', function (e) {
        if (!window.confirm(el.getAttribute('data-confirm'))) {
          e.preventDefault();
        }
      });
    });
  }

  /* ── Filtre de tableau côté client ──────────────────────
     <input data-filter-target="#tbl" data-filter-count="#count">
     <select data-filter-attr="status" data-filter-target="#tbl"> */
  function initTableFilters() {
    var inputs = document.querySelectorAll('[data-filter-target]');
    if (!inputs.length) return;

    var groups = {};
    inputs.forEach(function (input) {
      var key = input.getAttribute('data-filter-target');
      (groups[key] = groups[key] || []).push(input);
    });

    Object.keys(groups).forEach(function (selector) {
      var table = document.querySelector(selector);
      if (!table) return;
      var rows = Array.prototype.slice.call(table.querySelectorAll('tbody tr[data-row]'));
      var controls = groups[selector];
      var counter = document.querySelector(
        controls.map(function (c) { return c.getAttribute('data-filter-count'); })
          .filter(Boolean)[0] || '#none');

      function apply() {
        var visible = 0;
        rows.forEach(function (row) {
          var show = controls.every(function (control) {
            var value = (control.value || '').trim().toLowerCase();
            if (!value) return true;
            var attr = control.getAttribute('data-filter-attr');
            if (attr) return (row.getAttribute('data-' + attr) || '').toLowerCase() === value;
            return (row.getAttribute('data-search') || row.textContent || '')
              .toLowerCase().indexOf(value) !== -1;
          });
          row.hidden = !show;
          if (show) visible++;
        });
        if (counter) counter.textContent = visible;
        var empty = table.querySelector('[data-filter-empty]');
        if (empty) empty.hidden = visible !== 0;
      }

      controls.forEach(function (control) {
        control.addEventListener('input', apply);
        control.addEventListener('change', apply);
      });
      apply();
    });
  }

  /* ── Lignes de tableau cliquables ───────────────────────
     <tr data-href="/url"> — le clic sur un bouton/lien reste prioritaire. */
  function initRowLinks() {
    document.querySelectorAll('tr[data-href]').forEach(function (row) {
      row.classList.add('row-link');
      row.addEventListener('click', function (e) {
        if (e.target.closest('a, button, input, label, select')) return;
        window.location.href = row.getAttribute('data-href');
      });
    });
  }

  /* ── Motif d'annulation obligatoire (sanctions) ──────────
     <form data-prompt="Question" data-prompt-field="cancelReason"> */
  function initPrompts() {
    document.querySelectorAll('[data-prompt]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        var field = form.querySelector('[name="' + form.getAttribute('data-prompt-field') + '"]');
        if (!field) return;
        var answer = window.prompt(form.getAttribute('data-prompt'));
        if (!answer || !answer.trim()) {
          e.preventDefault();
          return;
        }
        field.value = answer.trim();
      });
    });
  }

  /* ── Impression (<button data-print>) ───────────────────── */
  function initPrint() {
    document.querySelectorAll('[data-print]').forEach(function (btn) {
      btn.addEventListener('click', function () { window.print(); });
    });
  }

  /* ── Montant proposé selon l'option choisie ──────────────
     <select data-prefill-target="#champ"> avec <option data-prefill="2500">
     Le montant reste modifiable : c'est une proposition, pas une contrainte. */
  function initPrefill() {
    document.querySelectorAll('[data-prefill-target]').forEach(function (select) {
      var target = document.querySelector(select.getAttribute('data-prefill-target'));
      if (!target) return;

      function apply() {
        var option = select.options[select.selectedIndex];
        var value = option && option.getAttribute('data-prefill');
        if (value && Number(value) > 0) target.value = value;
        else target.value = '';
      }

      select.addEventListener('change', apply);
      // Au chargement, on ne remplit que si l'utilisateur n'a rien saisi —
      // un formulaire réaffiché après erreur garde sa valeur.
      if (!target.value) apply();
    });
  }

  /* ── Reprendre là où l'on était ──────────────────────────
     Un enregistrement renvoie sur la même page (motif POST-redirect-GET) :
     sans rien, le navigateur la rouvre en haut, sur le premier onglet. La
     position et l'onglet actif sont donc mémorisés à l'envoi du formulaire
     et rétablis au retour. Le message flash, lui, est collé en haut de la
     fenêtre : il reste visible sans qu'on ait à remonter. */
  function initReturnToPlace() {
    var KEY = 'asvosonk:return';

    document.addEventListener('submit', function (e) {
      var form = e.target;
      if (!form || (form.method || '').toLowerCase() === 'get') return;
      var tab = document.querySelector('.nav-tabs .nav-link.active');
      try {
        window.sessionStorage.setItem(KEY, JSON.stringify({
          path: window.location.pathname,
          y: window.scrollY,
          tab: tab ? tab.id : null
        }));
      } catch (err) { /* navigation privée : on s'en passe */ }
    }, true);

    var saved;
    try {
      saved = window.sessionStorage.getItem(KEY);
      window.sessionStorage.removeItem(KEY);
    } catch (err) { return; }
    if (!saved) return;

    var state;
    try { state = JSON.parse(saved); } catch (err) { return; }
    if (!state || state.path !== window.location.pathname) return;

    if (state.tab && window.bootstrap) {
      var button = document.getElementById(state.tab);
      if (button) window.bootstrap.Tab.getOrCreateInstance(button).show();
    }
    if (state.y > 0) {
      // Après l'affichage de l'onglet : sa hauteur change la position utile.
      window.requestAnimationFrame(function () { window.scrollTo(0, state.y); });
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    initPrint();
    initNavToggle();
    initFlash();
    initConfirm();
    initTableFilters();
    initRowLinks();
    initPrompts();
    initPrefill();
    initReturnToPlace();
  });
})();
