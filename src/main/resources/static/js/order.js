// Optional enhancements. Ordering works fully without this file; the server's amounts are final.
(function () {
  "use strict";
  var BN = "০১২৩৪৫৬৭৮৯";
  var toBangla = function (value) {
    return String(value).replace(/[0-9]/g, function (d) { return BN[Number(d)]; });
  };
  var toLatin = function (value) {
    return value.replace(/[০-৯]/g, function (d) { return BN.indexOf(d); });
  };
  var money = function (value) { return "৳" + toBangla(value); };

  var summary = document.querySelector("[data-order-summary]");
  var form = document.querySelector("[data-order-form]");
  if (form && summary) {
    var unit = Number(summary.dataset.unit);
    var charges = { INSIDE: Number(summary.dataset.inside), OUTSIDE: Number(summary.dataset.outside) };
    var shownTotal = form.querySelector('input[name="shownTotal"]');
    var quantityInput = form.querySelector('input[name="quantity"]');
    var set = function (name, text) {
      summary.querySelector('[data-line="' + name + '"]').textContent = text;
    };
    var update = function () {
      var raw = toLatin(quantityInput.value.trim());
      var quantity = /^[0-9]{1,2}$/.test(raw) ? Number(raw) : 0;
      var valid = quantity >= 1 && quantity <= 10;
      var area = form.querySelector('input[name="area"]:checked');
      var delivery = area ? charges[area.value] : null;
      var both = "ঢাকায় " + money(charges.INSIDE) + ", ঢাকার বাইরে " + money(charges.OUTSIDE);
      set("subtotal", valid ? money(unit) + " × " + toBangla(quantity) + " = " + money(unit * quantity) : "সঠিক পরিমাণ লিখুন");
      set("delivery", delivery === null ? both : money(delivery));
      if (valid && delivery !== null) {
        var total = unit * quantity + delivery;
        var advance = Math.floor((total + 1) / 2);
        set("total", money(total));
        set("advance", money(advance));
        set("balance", money(total - advance));
        shownTotal.value = String(total);
      } else {
        var waiting = valid ? "এলাকা বাছলে দেখাবে" : "সঠিক পরিমাণ লিখলে দেখাবে";
        set("total", waiting);
        set("advance", waiting);
        set("balance", waiting);
        shownTotal.value = "";
      }
    };
    form.addEventListener("input", update);
    form.addEventListener("change", update);
    window.addEventListener("pageshow", update);
    var quoteButton = form.querySelector('button[value="quote"]');
    if (quoteButton) quoteButton.hidden = true;
    update();
  }

  var errorSummary = document.getElementById("order-errors");
  // Wait until after load: the browser's jump to #order would otherwise clear the focus.
  if (errorSummary) {
    window.addEventListener("load", function () {
      setTimeout(function () { errorSummary.focus(); }, 0);
    });
  }

  var copy = document.querySelector("[data-copy-code]");
  if (copy && navigator.clipboard) {
    copy.hidden = false;
    var label = copy.textContent;
    copy.addEventListener("click", function () {
      navigator.clipboard.writeText(copy.dataset.copyCode).then(function () {
        copy.textContent = "কপি হয়েছে";
        setTimeout(function () { copy.textContent = label; }, 2000);
      });
    });
  }
})();
