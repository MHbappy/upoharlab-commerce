// Loaded only when a Pixel ID is configured. Lives in a file because the site forbids inline scripts.
(function () {
  "use strict";
  var tag = document.currentScript;
  if (!tag || !tag.dataset.pixel) return;
  var fbq = window.fbq = function () {
    fbq.callMethod ? fbq.callMethod.apply(fbq, arguments) : fbq.queue.push(arguments);
  };
  if (!window._fbq) window._fbq = fbq;
  fbq.push = fbq;
  fbq.loaded = true;
  fbq.version = "2.0";
  fbq.queue = [];
  var script = document.createElement("script");
  script.async = true;
  script.src = "https://connect.facebook.net/en_US/fbevents.js";
  document.head.appendChild(script);
  fbq("init", tag.dataset.pixel);
  fbq("track", "PageView");
  if (tag.dataset.purchase) {
    // A reloaded thank-you page must not count as a second sale.
    var key = "upohar-purchase-" + tag.dataset.purchase, seen = false;
    try { seen = sessionStorage.getItem(key) === "1"; sessionStorage.setItem(key, "1"); } catch (e) {}
    if (!seen) fbq("track", "Purchase", { value: Number(tag.dataset.value), currency: "BDT" }, { eventID: tag.dataset.purchase });
    return;
  }
  fbq("track", "ViewContent", { content_name: tag.dataset.product, currency: "BDT", value: Number(tag.dataset.value) });
  document.addEventListener("lp:checkout", function () { fbq("track", "InitiateCheckout"); }, { once: true });
})();
