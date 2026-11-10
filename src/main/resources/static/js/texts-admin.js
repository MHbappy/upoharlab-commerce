// Admin → Site text: find a text, put one back to its default, confirm a full reset.
(function () {
  "use strict";
  document.querySelectorAll("[data-js-only]").forEach(function (el) { el.hidden = false; });

  var search = document.getElementById("text-search");
  var count = document.getElementById("text-search-count");
  var fields = Array.prototype.slice.call(document.querySelectorAll(".text-field"));
  var groups = Array.prototype.slice.call(document.querySelectorAll(".texts-group"));

  if (search) {
    search.addEventListener("input", function () {
      var q = search.value.trim().toLowerCase();
      var shown = 0;
      fields.forEach(function (f) {
        var area = f.querySelector("textarea");
        var hit = !q || (f.dataset.search + " " + area.value.toLowerCase()).indexOf(q) !== -1;
        f.hidden = !hit;
        if (hit) shown++;
      });
      groups.forEach(function (g) {
        var any = g.querySelector(".text-field:not([hidden])");
        g.hidden = !any;
        if (q && any) g.open = true;
      });
      count.textContent = q ? shown + (shown === 1 ? " matching text" : " matching texts") : "";
    });
  }

  document.querySelectorAll(".text-use-default").forEach(function (button) {
    button.addEventListener("click", function () {
      var area = button.parentElement.querySelector("textarea");
      area.value = area.dataset.default;
      area.focus();
    });
  });

  document.querySelectorAll("form[data-confirm]").forEach(function (form) {
    form.addEventListener("submit", function (event) {
      if (!window.confirm(form.dataset.confirm)) event.preventDefault();
    });
  });
})();
