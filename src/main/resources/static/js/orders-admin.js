// Small conveniences for the admin order pages. Everything still works without this file.
(function () {
  var all = document.getElementById("select-all");
  if (all) {
    all.closest("[data-js-only]").hidden = false;
    var ticks = document.querySelectorAll(".order-tick");
    all.addEventListener("change", function () {
      ticks.forEach(function (tick) { tick.checked = all.checked; });
    });
  }
  document.querySelectorAll("[data-print]").forEach(function (button) {
    button.addEventListener("click", function () { window.print(); });
  });
})();
