// Conveniences for the landing page editor. Spare rows and number boxes work without this file.
(function () {
  "use strict";
  function renumber(row, prefix, index) {
    var pattern = new RegExp("^" + prefix.replace(/[.]/g, "\\.") + "\\[\\d+\\]");
    var filePrefix = "landingUpload/" + prefix.replace(/[.]/g, "/");
    var filePattern = new RegExp("^" + filePrefix + "\\(\\d+\\)");
    row.querySelectorAll("[name]").forEach(function (el) {
      el.name = el.name.replace(pattern, prefix + "[" + index + "]")
        .replace(filePattern, filePrefix + "(" + index + ")");
    });
  }
  function isBlank(row) {
    return !Array.prototype.some.call(
      row.querySelectorAll('input[type=text],input[type=number],textarea'),
      function (el) { return el.value.trim() !== ""; }
    ) && !Array.prototype.some.call(row.querySelectorAll("[data-image-picker],[data-font-pick]"), function (el) { return el.value; });
  }
  function clearRow(row) {
    row.querySelectorAll("input[type=text],input[type=number],input[type=file],textarea").forEach(function (el) { el.value = ""; });
    row.querySelectorAll("input[type=radio]").forEach(function (el) { el.checked = false; });
    row.querySelectorAll("select").forEach(function (el) { el.selectedIndex = 0; });
    row.querySelectorAll("[data-slot-thumb]").forEach(function (el) { el.hidden = true; el.removeAttribute("src"); });
    row.querySelectorAll("[data-swatch]").forEach(function (el) { el.value = "#2b211c"; });
    // Lets a design row put away its image and text box.
    row.querySelectorAll("[data-stage]").forEach(function (stage) {
      stage.hidden = true;
      var help = stage.parentNode.querySelector("[data-stage-help]");
      if (help) help.hidden = true;
    });
  }
  // Empty spare rows stay in the page for saving but out of sight until asked for.
  function collapseBlankRows(box) {
    var rows = box.querySelectorAll(":scope > [data-row]");
    var anyFilled = Array.prototype.some.call(rows, function (r) { return !isBlank(r); });
    rows.forEach(function (row, i) { row.hidden = isBlank(row) && (anyFilled || i > 0); });
  }
  function collapseValues(box) {
    var rows = Array.prototype.slice.call(box.querySelectorAll("[data-value-row]"));
    var more = box.querySelector("[data-more-values]");
    var firstBlank = true;
    rows.forEach(function (row) {
      row.hidden = false;
      if (isBlank(row)) { row.hidden = !firstBlank; firstBlank = false; }
    });
    var MAX_VALUES = 12;
    function visible() { return box.querySelectorAll("[data-value-row]:not([hidden])").length; }
    function refresh() {
      more.hidden = visible() >= MAX_VALUES;
      box.querySelectorAll("[data-remove-value]").forEach(function (button) { button.hidden = false; });
    }
    more.onclick = function () {
      var next = box.querySelector("[data-value-row][hidden]");
      if (next) next.hidden = false;
      else {
        var all = box.querySelectorAll("[data-value-row]");
        next = all[all.length - 1].cloneNode(true);
        next.querySelectorAll("[name]").forEach(function (el) {
          el.name = el.name.replace(/\.values\[\d+\]/, ".values[" + all.length + "]")
            .replace(/\/values\(\d+\)/, "/values(" + all.length + ")");
        });
        clearRow(next);
        box.insertBefore(next, more);
      }
      next.querySelector("input[type=text]").focus();
      refresh();
    };
    box.onclick = function (event) {
      if (!event.target.matches("[data-remove-value]")) return;
      var row = event.target.closest("[data-value-row]");
      clearRow(row);
      if (visible() > 1) row.hidden = true;
      refresh();
    };
    refresh();
  }
  document.querySelectorAll("[data-values]").forEach(collapseValues);
  document.querySelectorAll("[data-repeat]").forEach(function (box) {
    var add = box.querySelector("[data-add-row]");
    if (!add) return;
    add.hidden = false;
    collapseBlankRows(box);
    box.querySelectorAll("[data-remove-row]").forEach(function (button) { button.hidden = false; });
    // Stops at the limit the server checks; a removed row frees a place again.
    function refreshAdd() {
      var max = +box.dataset.max;
      if (max) add.hidden = box.querySelectorAll(":scope > [data-row]:not([hidden])").length >= max;
    }
    function reveal(row) {
      refreshAdd();
      var input = row.querySelector("textarea,input[type=text],[data-font-pick]");
      if (box.dataset.max && input) input.focus();
      if (box.scrollWidth > box.clientWidth) row.scrollIntoView({ inline: "start", block: "nearest" });
    }
    box.addEventListener("click", function (event) {
      if (!event.target.matches("[data-remove-row]")) return;
      var row = event.target.closest("[data-row]");
      clearRow(row);
      row.querySelectorAll("[data-values]").forEach(collapseValues);
      // One empty row always stays, so there is somewhere to type.
      if (box.querySelectorAll(":scope > [data-row]:not([hidden])").length > 1) row.hidden = true;
      keepOneMarked();
      refreshAdd();
    });
    // Packages always have one marked, so the choice is visible before saving.
    function keepOneMarked() {
      var radios = box.querySelectorAll(":scope > [data-row]:not([hidden]) input[type=radio]");
      if (radios.length && !Array.prototype.some.call(radios, function (r) { return r.checked; })) radios[0].checked = true;
    }
    keepOneMarked();
    refreshAdd();
    add.addEventListener("click", function () {
      var hidden = box.querySelector(":scope > [data-row][hidden]");
      if (hidden) { hidden.hidden = false; reveal(hidden); return; }
      var rows = box.querySelectorAll(":scope > [data-row]");
      var copy = rows[rows.length - 1].cloneNode(true);
      renumber(copy, box.dataset.repeat, rows.length);
      copy.querySelectorAll("input,textarea").forEach(function (el) {
        if (el.type === "radio") { el.value = rows.length; el.checked = false; }
        else if (el.type === "color") el.value = "#2b211c";
        else el.value = "";
      });
      copy.querySelectorAll("select").forEach(function (el) { el.selectedIndex = 0; });
      copy.querySelectorAll("[data-slot-thumb]").forEach(function (el) { el.hidden = true; el.removeAttribute("src"); });
      box.insertBefore(copy, add);
      copy.querySelectorAll("[data-values]").forEach(collapseValues);
      if (copy.matches("[data-design-row]")) setUp(copy);
      reveal(copy);
    });
  });

  // A new file and a saved image are alternatives, so choosing one clears the other.
  document.addEventListener("change", function (event) {
    var slot = event.target.closest && event.target.closest("[data-image-slot]");
    if (!slot) return;
    var thumb = slot.querySelector("[data-slot-thumb]");
    var picked = slot.querySelector("[data-image-file]").files[0];
    if (event.target.matches("[data-image-picker]") && event.target.value) picked = null;
    var saved = slot.querySelector("[data-image-picker]").value;
    var source = picked ? URL.createObjectURL(picked) : saved ? "/media/thumb-" + saved : null;
    thumb.hidden = !source;
    if (source) thumb.src = source; else thumb.removeAttribute("src");
    if (event.target.matches("[data-image-file]") && event.target.files.length) {
      slot.querySelector("[data-image-picker]").selectedIndex = 0;
    } else if (event.target.matches("[data-image-picker]") && event.target.value) {
      slot.querySelector("[data-image-file]").value = "";
    }
  });

  // A font already in another row cannot be picked twice.
  function syncFonts() {
    var picks = Array.prototype.slice.call(document.querySelectorAll("[data-font-pick]"));
    picks.forEach(function (pick) {
      Array.prototype.forEach.call(pick.options, function (option) {
        option.disabled = !!option.value && picks.some(function (other) { return other !== pick && other.value === option.value; });
      });
    });
  }
  document.addEventListener("change", function (event) { if (event.target.matches("[data-font-pick]")) syncFonts(); });
  document.addEventListener("click", function (event) { if (event.target.matches('[data-repeat="preview.fonts"] [data-remove-row]')) syncFonts(); });
  syncFonts();

  // A small picture of each product photo as soon as it is chosen.
  document.addEventListener("change", function (event) {
    if (!event.target.matches("[data-photo-file]")) return;
    var thumb = event.target.parentNode.querySelector("[data-slot-thumb]");
    var picked = event.target.files[0];
    thumb.hidden = !picked;
    if (picked) thumb.src = URL.createObjectURL(picked); else thumb.removeAttribute("src");
  });

  var STORED_MAX = 1200; // the server resizes every image to fit this, and positions refer to that size

  // A colour box beside each code, so nobody has to know the code format.
  var HEX = /^#[0-9a-fA-F]{6}$/;
  function matchSwatch(text) {
    var swatch = text.previousElementSibling;
    if (swatch && swatch.matches("[data-swatch]") && HEX.test(text.value)) swatch.value = text.value.toLowerCase();
  }
  function addSwatch(text) {
    if (text.previousElementSibling && text.previousElementSibling.matches("[data-swatch]")) return;
    var pair = document.createElement("span");
    pair.className = "color-pair";
    var swatch = document.createElement("input");
    swatch.type = "color";
    swatch.className = "color-swatch";
    swatch.setAttribute("data-swatch", "");
    swatch.setAttribute("aria-label", "Pick colour");
    swatch.value = "#2b211c";
    text.parentNode.insertBefore(pair, text);
    pair.appendChild(swatch);
    pair.appendChild(text);
    matchSwatch(text);
  }
  document.addEventListener("input", function (event) {
    if (event.target.matches("[data-swatch]")) event.target.nextElementSibling.value = event.target.value;
    else if (event.target.matches("[data-color],[data-shadow]")) matchSwatch(event.target);
  });

  function setUp(row) {
    row.querySelectorAll("[data-color],[data-shadow]").forEach(addSwatch);
    var stage = row.querySelector("[data-stage]");
    var image = row.querySelector("[data-stage-image]");
    var box = row.querySelector("[data-box]");
    var help = row.querySelector("[data-stage-help]");
    var picker = row.querySelector("[data-image-picker]");
    var file = row.querySelector("[data-image-file]");
    var blobUrl = null;
    var min = row.querySelector("[data-min]"), color = row.querySelector("[data-color]");
    var x = row.querySelector("[data-x]"), y = row.querySelector("[data-y]");
    var width = row.querySelector("[data-width]"), size = row.querySelector("[data-size]");
    var shrink = function () {
      return Math.min(1, STORED_MAX / Math.max(image.naturalWidth, image.naturalHeight));
    };
    var scale = function () { return image.clientWidth / (image.naturalWidth * shrink()); };

    // Sensible starting values, so a new design is usable as soon as its image is chosen.
    function fillBlanks() {
      var w = image.naturalWidth * shrink(), h = image.naturalHeight * shrink();
      if (!x.value) x.value = Math.round(w / 2);
      if (!y.value) y.value = Math.round(h / 2);
      if (!width.value) width.value = Math.round(w * 0.8);
      if (!size.value) size.value = Math.round(h / 5);
      if (!min.value) min.value = Math.max(1, Math.round(h / 15));
      if (!color.value) { color.value = "#2B211C"; matchSwatch(color); }
    }

    function draw() {
      if (!image.naturalWidth) return;
      var s = scale(), w = (+width.value || 200) * s, h = (+size.value || 60) * s * 1.4;
      box.style.width = w + "px";
      box.style.height = h + "px";
      box.style.left = ((+x.value || 0) * s - w / 2) + "px";
      box.style.top = ((+y.value || 0) * s - h / 2) + "px";
    }
    function load() {
      if (blobUrl) { URL.revokeObjectURL(blobUrl); blobUrl = null; }
      var chosen = file.files[0];
      if (chosen) blobUrl = URL.createObjectURL(chosen);
      var source = blobUrl || (picker.value ? "/media/" + picker.value : null);
      stage.hidden = !source;
      help.hidden = !source;
      if (source) image.src = source;
    }
    function drag(start, onMove) {
      start.preventDefault();
      var from = { x: start.clientX, y: start.clientY };
      var move = function (e) { onMove((e.clientX - from.x) / scale(), (e.clientY - from.y) / scale()); draw(); };
      var stop = function () {
        window.removeEventListener("pointermove", move);
        window.removeEventListener("pointerup", stop);
      };
      window.addEventListener("pointermove", move);
      window.addEventListener("pointerup", stop);
    }
    box.addEventListener("pointerdown", function (e) {
      var isHandle = e.target.matches("[data-handle]");
      var base = { x: +x.value || 0, y: +y.value || 0, w: +width.value || 200 };
      drag(e, function (dx, dy) {
        if (isHandle) {
          width.value = Math.max(1, Math.round(base.w + dx * 2));
        } else {
          x.value = Math.max(0, Math.round(base.x + dx));
          y.value = Math.max(0, Math.round(base.y + dy));
        }
      });
    });
    image.addEventListener("load", function () { fillBlanks(); draw(); });
    picker.addEventListener("change", load);
    file.addEventListener("change", load);
    [x, y, width, size].forEach(function (el) { el.addEventListener("input", draw); });
    window.addEventListener("resize", draw);
    load();
  }
  document.querySelectorAll("[data-design-row]").forEach(setUp);

  // Section switches: a hidden section folds away, and its entries are still saved.
  var toggles = Array.prototype.slice.call(document.querySelectorAll("[data-section-toggle]"));
  function navLink(id) { return document.querySelector('.editor-nav a[href="#' + id + '"]'); }
  function refreshSections() {
    var showing = 0;
    toggles.forEach(function (toggle) {
      var on = toggle.checked, box = toggle.closest("fieldset");
      if (on) showing++;
      box.classList.toggle("section-off", !on);
      toggle.closest("label").querySelector("[data-switch-label]").textContent = on ? "Showing on customer page" : "Hidden from customers";
      var link = navLink(box.id);
      if (link) link.classList.toggle("is-off", !on);
      document.querySelectorAll('[data-follows="' + toggle.dataset.key + '"]').forEach(function (follower) {
        follower.hidden = !on;
        var followerLink = navLink(follower.id);
        if (followerLink) followerLink.hidden = !on;
      });
    });
    var count = document.querySelector("[data-section-count]");
    if (count) count.textContent = showing + " of " + toggles.length + " page sections showing";
    window.dispatchEvent(new Event("resize"));
  }
  toggles.forEach(function (toggle) { toggle.addEventListener("change", refreshSections); });
  refreshSections();

  // Browsers empty file boxes whenever a page reloads, so the form is sent in the background.
  // A rejected save then only adds its messages, and every chosen image stays in place.
  var form = document.getElementById("product-form");
  var SAVED_MESSAGE = "upoharlab-product-saved";

  function showSavedMessage() {
    var text = null;
    try {
      text = sessionStorage.getItem(SAVED_MESSAGE);
      sessionStorage.removeItem(SAVED_MESSAGE);
    } catch (ignored) {}
    if (!text) return;
    var notice = document.createElement("p");
    notice.className = "notice";
    notice.setAttribute("role", "status");
    notice.textContent = text;
    var main = document.querySelector("main");
    main.insertBefore(notice, main.firstChild);
  }

  // Each message line jumps to the box it is about.
  var JUMPS = [
    [/Sample text/, '[name="preview.defaultText"]'],
    [/Maximum characters/, '[name="preview.maxChars"]'],
    [/preview font/, '[name="preview.fonts[0]"]'],
    [/shop list/, "#lp-packages"],
    [/hero image/, '[name="landingUpload/hero/heroImage"]'],
    [/headline/, '[name="hero.headline"]'],
    [/offer price/, '[name="hero.offerPrice"]'],
    [/benefit/, "#lp-benefits"],
    [/package/, "#lp-packages"],
    [/option|extra/, "#lp-options"],
    [/advance/, '[name="advanceAmount"]'],
    [/delivery/, '[name="delivery.insideDhakaCharge"]'],
    [/review|customer count/, "#lp-proof"],
    [/photo/, "#photos"]
  ];
  function jumpTarget(text) {
    var design = /preview design (\d+)/.exec(text);
    if (design) {
      // Removed rows are not saved, so the message counts only the designs still in use.
      var rows = Array.prototype.filter.call(
        document.querySelectorAll('[data-repeat="preview.designs"] > [data-row]'),
        function (row) { return !row.hidden && !isBlank(row); });
      var row = rows[+design[1] - 1];
      if (!row) return document.getElementById("lp-preview");
      return row.querySelector(/Base image/.test(text) ? "[data-image-file]"
        : /colour/.test(text) ? (/shadow/.test(text) ? "[data-shadow]" : "[data-color]")
        : "input[type=text]");
    }
    for (var i = 0; i < JUMPS.length; i++) if (JUMPS[i][0].test(text)) return document.querySelector(JUMPS[i][1]);
    return null;
  }
  function jumpTo(target) {
    var box = target.closest("[data-section-body]") ? target.closest("fieldset") : target.closest("[data-follows]");
    var toggle = box && (box.dataset.follows
      ? document.querySelector('[data-section-toggle][data-key="' + box.dataset.follows + '"]')
      : box.querySelector("[data-section-toggle]"));
    if (toggle && !toggle.checked) { toggle.checked = true; refreshSections(); }
    var hidden = target.closest("[hidden]");
    if (hidden) hidden.hidden = false;
    target.scrollIntoView({ block: "center" });
    if (target.focus) target.focus({ preventScroll: true });
  }
  function linkErrors(summary) {
    var linked = false;
    summary.querySelectorAll("li").forEach(function (item) {
      var target = jumpTarget(item.textContent);
      if (!target) return;
      linked = true;
      (target.closest(".field") || target).classList.add("field-flagged");
      var link = document.createElement("a");
      link.href = "#";
      link.textContent = item.textContent.replace(/^Landing page: /, "");
      link.addEventListener("click", function (event) { event.preventDefault(); jumpTo(target); });
      item.textContent = "";
      item.appendChild(link);
    });
    if (!linked) return;
    var hint = document.createElement("p");
    hint.textContent = "Click a line below to go to that box.";
    summary.insertBefore(hint, summary.querySelector("ul"));
  }

  function clearMessages() {
    document.querySelectorAll(".field-flagged").forEach(function (el) { el.classList.remove("field-flagged"); });
    document.querySelectorAll("[data-form-errors]").forEach(function (el) { el.remove(); });
    form.querySelectorAll("p.error").forEach(function (el) { el.remove(); });
  }

  function showSummary(summary) {
    form.parentNode.insertBefore(summary, form);
    linkErrors(summary);
    summary.scrollIntoView({ block: "center" });
    summary.focus({ preventScroll: true });
  }

  function showFailure(text) {
    clearMessages();
    var summary = document.createElement("div");
    summary.className = "error";
    summary.setAttribute("role", "alert");
    summary.setAttribute("data-form-errors", "");
    summary.tabIndex = -1;
    var line = document.createElement("p");
    line.textContent = text;
    summary.appendChild(line);
    showSummary(summary);
  }

  function showErrors(page) {
    clearMessages();
    page.querySelectorAll("#product-form p.error").forEach(function (message) {
      var box = message.closest(".field") || message.closest("fieldset");
      var control = box && box.querySelector("[id]");
      var field = control && document.getElementById(control.id);
      if (!field) return;
      (field.closest(".field") || field.closest("fieldset")).appendChild(document.adoptNode(message));
    });
    form.querySelectorAll("details").forEach(function (details) {
      if (details.querySelector("p.error")) details.open = true;
    });
    var summary = page.querySelector("[data-form-errors]");
    if (!summary) { showFailure("The product could not be saved. Please try again."); return; }
    var reselect = summary.querySelector("[data-reselect]");
    if (reselect) reselect.remove();
    showSummary(document.adoptNode(summary));
  }

  if (form) {
    showSavedMessage();
    var rendered = document.querySelector("[data-form-errors]");
    if (rendered) linkErrors(rendered);
    var button = form.querySelector(".product-submit button");
    var sending = false;
    form.addEventListener("submit", function (event) {
      event.preventDefault();
      if (sending) return;
      sending = true;
      button.disabled = true;
      fetch(form.getAttribute("action"), { method: "POST", body: new FormData(form), credentials: "same-origin" })
        .then(function (response) {
          return response.text().then(function (html) {
            var page = new DOMParser().parseFromString(html, "text/html");
            if (response.redirected) {
              var notice = page.querySelector("main .notice");
              try { if (notice) sessionStorage.setItem(SAVED_MESSAGE, notice.textContent); } catch (ignored) {}
              window.location.href = response.url;
              return;
            }
            if (response.ok && page.getElementById("product-form")) { showErrors(page); return; }
            showFailure(response.status === 413
              ? "The images are too large to send together. Remove or shrink some, then save again. Nothing was saved."
              : "The product could not be saved. Your entries are still here, so please try again.");
          });
        })
        .catch(function () {
          showFailure("The connection failed and nothing was saved. Your entries are still here, so please try again.");
        })
        .then(function () { sending = false; button.disabled = false; });
    });
  }
})();
