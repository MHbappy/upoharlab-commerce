// Enhances the order page. Everything can still be ordered without it; the server's amounts are final.
(function () {
  "use strict";
  var source = document.getElementById("landing-config");
  if (!source) return;
  var config = JSON.parse(source.textContent);
  var BN = "০১২৩৪৫৬৭৮৯";
  var bn = function (v) { return String(v).replace(/[0-9]/g, function (d) { return BN[+d]; }); };
  var money = function (v) { return "৳" + bn(v); };
  var $ = function (id) { return document.getElementById(id); };
  var form = $("lp-form");
  var field = function (name) { return form ? form.elements[name] : null; };

  document.querySelectorAll("[data-js-only]").forEach(function (el) { el.hidden = false; });
  document.querySelectorAll("[data-no-js-only]").forEach(function (el) { el.hidden = true; });

  // Live preview -----------------------------------------------------------------------------
  var svg = $("lp-svg"), textEl = $("lp-text"), engraving = $("engraving"), input = $("lp-input");
  var state = { font: null, design: 0 };
  var designs = config.preview.designs;

  var mediaUrl = function (file) { return "/media/" + file; };
  var checked = function (name) {
    var el = form && form.querySelector('input[name="' + name + '"]:checked');
    return el ? el.value : null;
  };

  function fit() {
    if (!textEl) return;
    var d = designs[state.design];
    if (!d || !d.fontSize || !d.maxWidth) return;
    var size = d.fontSize;
    textEl.setAttribute("font-size", size);
    var width = textEl.getComputedTextLength();
    if (width > d.maxWidth) {
      size = Math.max(d.minFontSize, Math.floor(size * d.maxWidth / width));
      textEl.setAttribute("font-size", size);
      while (size > d.minFontSize && textEl.getComputedTextLength() > d.maxWidth) {
        size -= 1;
        textEl.setAttribute("font-size", size);
      }
    }
    if ($("lp-font-size")) $("lp-font-size").value = size;
    var warn = $("lp-too-wide");
    if (warn) warn.hidden = textEl.getComputedTextLength() <= d.maxWidth;
  }

  // Measuring before the chosen font arrives would give the width of the fallback font.
  function fitWhenReady() {
    if (!textEl) return;
    fit();
    var load = state.font && document.fonts && document.fonts.load
      ? document.fonts.load('16px "' + state.font + '"', textEl.textContent || "ক")
      : Promise.resolve();
    load.catch(function () {}).then(function () { return document.fonts ? document.fonts.ready : null; }).then(fit);
  }

  function setText(value, from) {
    if (input && from !== input) input.value = value;
    if (engraving && from !== engraving) engraving.value = value;
    // Sample text belongs only in the illustration, never in the submitted order.
    if (textEl) textEl.textContent = value || config.preview.defaultText || "আপনার নাম";
    if ($("lp-example")) $("lp-example").hidden = !!value;
    document.querySelectorAll("[data-character-count]").forEach(function (counter) { counter.textContent = bn(Array.from(value).length); });
    document.querySelectorAll("[data-font-sample] > span").forEach(function (sample) {
      sample.textContent = value || "আপনার নাম";
    });
    updateSelectionRecap();
    fitWhenReady();
  }

  function setFont(name) {
    state.font = name;
    document.querySelectorAll("[data-font]").forEach(function (button) {
      button.setAttribute("aria-pressed", String(button.dataset.font === name));
    });
    var radio = form && form.querySelector('input[name="font"][value="' + CSS.escape(name) + '"]');
    if (radio) radio.checked = true;
    if (textEl && name) textEl.setAttribute("font-family", name);
    fitWhenReady();
  }

  function setDesign(index) {
    if (!designs[index]) return;
    state.design = index;
    document.querySelectorAll("[data-design]").forEach(function (button) {
      button.setAttribute("aria-pressed", String(+button.dataset.design === index));
    });
    var radio = form && form.querySelector('input[name="design"][value="' + index + '"]');
    if (radio && radio.type === "radio") radio.checked = true;
    var d = designs[index];
    var box = "0 0 " + d.imageWidth + " " + d.imageHeight;
    if (svg) svg.setAttribute("viewBox", box);
    var base = $("lp-base");
    if (d.baseImage) {
      if (!base && svg) {
        base = document.createElementNS("http://www.w3.org/2000/svg", "image");
        base.id = "lp-base";
        ["width", "height"].forEach(function (a) { base.setAttribute(a, "100%"); });
        base.setAttribute("preserveAspectRatio", "xMidYMid slice");
        $("lp-art").insertBefore(base, textEl);
      }
      if (base) base.setAttribute("href", mediaUrl(d.baseImage));
    } else if (base) { base.remove(); }
    if (textEl) {
      textEl.setAttribute("x", d.textX);
      textEl.setAttribute("y", d.textY);
      textEl.setAttribute("fill", d.color);
      if (d.shadow) {
        textEl.setAttribute("filter", "url(#lp-shadow)");
        $("lp-shadow-drop").setAttribute("flood-color", d.shadow);
      } else { textEl.removeAttribute("filter"); }
    }
    fitWhenReady();
  }

  if (engraving) engraving.addEventListener("input", function () { setText(engraving.value, engraving); });
  if (input) input.addEventListener("input", function () { setText(input.value, input); });
  document.querySelectorAll("[data-font]").forEach(function (button) {
    button.style.fontFamily = '"' + button.dataset.font + '", sans-serif';
    button.textContent = "আপনার নাম";
    button.addEventListener("click", function () { setFont(button.dataset.font); });
  });
  document.querySelectorAll("[data-design]").forEach(function (button) {
    button.addEventListener("click", function () { setDesign(+button.dataset.design); });
  });
  document.querySelectorAll("[data-font-sample]").forEach(function (sample) {
    if (config.preview.fonts.indexOf(sample.dataset.fontSample) !== -1)
      sample.style.fontFamily = '"' + sample.dataset.fontSample + '", sans-serif';
  });
  if (textEl) {
    state.font = checked("font") || config.preview.fonts[0];
    setDesign(+(checked("design") || 0));
    setFont(state.font);
  }
  setText(engraving ? engraving.value : "");

  function updateSelectionRecap() {
    var recap = $("lp-selection-text");
    if (!recap || !form) return;
    var pack = config.packages[+checked("packageIndex")];
    recap.textContent = [(engraving && engraving.value.trim()) || "লেখা যোগ করুন", pack && pack.name].filter(Boolean).join(" · ");
  }

  // Choices made in the sales sections stay selected in the final order form.
  function markChoices() {
    document.querySelectorAll("[data-option]").forEach(function (button) {
      button.setAttribute("aria-pressed", String(checked("opt" + button.dataset.option) === button.dataset.value));
    });
    document.querySelectorAll("[data-card]").forEach(function (card) {
      var selected = card.dataset.card === checked("packageIndex");
      card.classList.toggle("lp-card-chosen", selected);
      var link = card.querySelector("[data-pick-package]");
      if (link) {
        link.textContent = selected ? "এটিই নেব ✓" : "এটিই নেব →";
        if (selected) link.setAttribute("aria-current", "true"); else link.removeAttribute("aria-current");
      }
    });
    updateSelectionRecap();
  }
  if (form) {
    document.querySelectorAll("[data-option]").forEach(function (button) {
      button.addEventListener("click", function () {
        var radio = form.querySelector('input[name="opt' + button.dataset.option + '"][value="' + button.dataset.value + '"]');
        if (radio) { radio.checked = true; updateTotal(); }
      });
    });
    document.querySelectorAll("[data-pick-package]").forEach(function (link) {
      link.addEventListener("click", function () {
        var radio = form.querySelector('input[name="packageIndex"][value="' + link.dataset.pickPackage + '"]');
        if (radio) { radio.checked = true; updateTotal(); }
      });
    });
    document.querySelectorAll('a[href="#order"]').forEach(function (link) {
      link.addEventListener("click", function () {
        var details = $("order-customization");
        if (details) details.open = !input || !engraving.value.trim() || !!$("order-errors");
      });
    });
  }

  // Total ------------------------------------------------------------------------------------
  function updateTotal() {
    markChoices();
    if (!form || !form.querySelector("[data-line=package]")) return;
    var pack = config.packages[+checked("packageIndex")];
    if (!pack) return;
    var extras = 0;
    config.options.forEach(function (option, i) {
      var chosen = option.values[+checked("opt" + i)];
      if (chosen) extras += chosen.priceDelta;
    });
    form.querySelectorAll('input[name="upsells"]:checked').forEach(function (box) {
      extras += config.upsells[+box.value].price;
    });
    var area = checked("area");
    var delivery = area === "INSIDE" ? config.delivery.insideDhakaCharge
      : area === "OUTSIDE" ? config.delivery.outsideDhakaCharge : null;
    var set = function (name, text) { var el = form.querySelector('[data-line="' + name + '"]'); if (el) el.textContent = text; };
    var waiting = "এলাকা বাছলে দেখাবে";
    set("package", money(pack.price));
    set("extras", money(extras));
    set("delivery", delivery === null ? waiting : money(delivery));
    var total = delivery === null ? null : pack.price + extras + delivery;
    set("total", total === null ? "—" : money(total));
    field("shownTotal").value = total === null ? "" : String(total);
    var advance = total === null ? config.advanceAmount : Math.min(config.advanceAmount, total);
    document.querySelectorAll("[data-selected-price]").forEach(function (el) { el.textContent = money(pack.price + extras); });
    document.querySelectorAll("[data-old-price]").forEach(function (el) {
      el.hidden = !pack.oldPrice || extras !== 0;
      el.textContent = pack.oldPrice ? money(pack.oldPrice) : "";
    });
    document.querySelectorAll("[data-package-name]").forEach(function (el) { el.textContent = pack.name; });
    document.querySelectorAll("[data-payment-note]").forEach(function (el) {
      el.textContent = advance > 0
        ? "ডিজাইনে সম্মতির পরে " + money(advance) + " অগ্রিম। বাকি টাকা হাতে পেয়ে।"
        : "কোনো অগ্রিম নেই। পুরো টাকা হাতে পেয়ে দেবেন।";
    });
  }

  if (form) {
    form.addEventListener("change", function (event) {
      if (event.target.name === "font") setFont(event.target.value);
      if (event.target.name === "design" && event.target.type === "radio") setDesign(+event.target.value);
      updateTotal();
    });
    window.addEventListener("pageshow", function () {
      updateTotal();
      if (engraving) setText(engraving.value);
      if (textEl) { setFont(checked("font") || config.preview.fonts[0]); setDesign(+(checked("design") || 0)); }
    });
    updateTotal();
    var firstTouch = function () {
      document.dispatchEvent(new CustomEvent("lp:checkout"));
      form.removeEventListener("focusin", firstTouch);
    };
    form.addEventListener("focusin", firstTouch);
  }

  // Keep the mobile CTA while browsing, but never over the form or keyboard.
  var sticky = $("lp-sticky"), order = $("order");
  if (sticky && order) {
    document.body.classList.add("has-order-bar");
    var scheduled = false;
    function refreshSticky() {
      scheduled = false;
      var editing = document.activeElement && document.activeElement.matches("input,textarea,select");
      sticky.hidden = window.innerWidth > 760 || editing || order.getBoundingClientRect().top < window.innerHeight;
    }
    function scheduleSticky() {
      if (!scheduled) { scheduled = true; window.requestAnimationFrame(refreshSticky); }
    }
    window.addEventListener("scroll", scheduleSticky, { passive: true });
    window.addEventListener("resize", scheduleSticky);
    document.addEventListener("focusin", scheduleSticky);
    document.addEventListener("focusout", scheduleSticky);
    refreshSticky();
  }

  // Share as a picture ---------------------------------------------------------------------------
  function asDataUrl(url) {
    return fetch(url).then(function (res) {
      if (!res.ok) throw new Error("fetch failed");
      return res.blob();
    }).then(function (blob) {
      return new Promise(function (resolve, reject) {
        var reader = new FileReader();
        reader.onload = function () { resolve(reader.result); };
        reader.onerror = reject;
        reader.readAsDataURL(blob);
      });
    });
  }

  function renderPng() {
    var d = designs[state.design];
    var clone = svg.cloneNode(true);
    clone.setAttribute("width", d.imageWidth);
    clone.setAttribute("height", d.imageHeight);
    var file = config.fontFiles[state.font];
    var image = clone.querySelector("#lp-base");
    return Promise.all([
      asDataUrl("/fonts/" + file),
      d.baseImage ? asDataUrl(mediaUrl(d.baseImage)) : Promise.resolve(null)
    ]).then(function (parts) {
      var style = document.createElementNS("http://www.w3.org/2000/svg", "style");
      style.textContent = '@font-face{font-family:"' + state.font + '";src:url(' + parts[0] + ')}';
      clone.insertBefore(style, clone.firstChild);
      if (image && parts[1]) image.setAttribute("href", parts[1]);
      var markup = new XMLSerializer().serializeToString(clone);
      return new Promise(function (resolve, reject) {
        var img = new Image();
        img.onload = function () {
          // Some browsers need a moment before an SVG image's embedded font is usable.
          setTimeout(function () {
            var canvas = document.createElement("canvas");
            canvas.width = d.imageWidth;
            canvas.height = d.imageHeight;
            canvas.getContext("2d").drawImage(img, 0, 0, d.imageWidth, d.imageHeight);
            canvas.toBlob(function (blob) { blob ? resolve(blob) : reject(new Error("no image")); }, "image/png");
          }, 150);
        };
        img.onerror = reject;
        img.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(markup);
      });
    });
  }

  var share = $("lp-share");
  if (share && svg) {
    var label = share.textContent;
    var message = "দেখো তো এই নামের ডিজাইনটা কেমন লাগছে? " + location.origin + location.pathname;
    share.addEventListener("click", function () {
      share.disabled = true;
      share.textContent = "ছবি তৈরি হচ্ছে…";
      renderPng().then(function (blob) {
        var picture = new File([blob], "upohar-preview.png", { type: "image/png" });
        if (navigator.canShare && navigator.canShare({ files: [picture] })) {
          return navigator.share({ files: [picture], text: message }).catch(function () {});
        }
        var link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = "upohar-preview.png";
        link.click();
        setTimeout(function () { URL.revokeObjectURL(link.href); }, 10000);
        window.open("https://wa.me/?text=" + encodeURIComponent(message), "_blank", "noopener");
      }).catch(function () {
        window.open("https://wa.me/?text=" + encodeURIComponent(message), "_blank", "noopener");
      }).then(function () {
        share.disabled = false;
        share.textContent = label;
      });
    });
  }

  function photoStrip(strip, thumbs) {
    if (!strip || !thumbs.length) return;
    var mark = function () {
      var current = Math.round(strip.scrollLeft / strip.clientWidth);
      thumbs.forEach(function (thumb, i) { thumb.setAttribute("aria-current", i === current ? "true" : "false"); });
    };
    thumbs.forEach(function (thumb, i) {
      thumb.addEventListener("click", function () { strip.scrollTo({ left: i * strip.clientWidth, behavior: "smooth" }); });
    });
    strip.addEventListener("scroll", mark, { passive: true });
    mark();
  }
  photoStrip($("lp-gallery"), document.querySelectorAll(".lp-gallery-box .lp-gallery-thumb"));
  photoStrip($("lp-hero-strip"), document.querySelectorAll(".lp-hero-thumb"));

  var problem = $("order-errors") || $("order-message");
  if (problem) window.addEventListener("load", function () { setTimeout(function () { problem.focus(); }, 0); });
})();
