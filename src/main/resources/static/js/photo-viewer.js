// Product photo viewer, built on PhotoSwipe 5 (served from /vendor, MIT licence).
// Tap a photo to open it full screen. Pinch, double-tap or mouse-wheel to zoom, drag to pan,
// swipe or use the arrows to move between photos. The phone's back gesture closes it.
// Each block of photos (top slider, details gallery, customer photos) is browsed on its own.
import PhotoSwipeLightbox from "../vendor/photoswipe-5.4.4/photoswipe-lightbox.esm.min.js";

const textSource = document.getElementById("landing-text");
const texts = textSource ? JSON.parse(textSource.textContent) : {};
const BN = "০১২৩৪৫৬৭৮৯";
const bn = (n) => String(n).replace(/[0-9]/g, (d) => BN[+d]);

const lightbox = new PhotoSwipeLightbox({
  pswpModule: () => import("../vendor/photoswipe-5.4.4/photoswipe.esm.min.js"),
  showHideAnimationType: "zoom",
  bgOpacity: 1,
  wheelToZoom: true,
  initialZoomLevel: "fit",
  // Steps relative to the fitted size, so a double-tap on a phone and a click on a desktop both
  // zoom 2.5 times and pinching can go to 4 times, whatever the screen size.
  secondaryZoomLevel: (zoom) => zoom.fit * 2.5,
  maxZoomLevel: (zoom) => zoom.fit * 4,
  counter: false,
  closeTitle: texts["product.zoom.close"] || "Close",
  zoomTitle: texts["product.zoom.zoom"] || "Zoom",
  arrowPrevTitle: texts["product.zoom.prev"] || "Previous",
  arrowNextTitle: texts["product.zoom.next"] || "Next",
  errorMsg: texts["product.zoom.error"] || "The image cannot be loaded",
});

// A counter in Bangla digits, e.g. "২ / ৩"; hidden for a single photo.
lightbox.on("uiRegister", () => {
  lightbox.pswp.ui.registerElement({
    name: "bn-counter",
    order: 5,
    isButton: false,
    appendTo: "bar",
    onInit: (el, pswp) => {
      const update = () => {
        el.textContent = pswp.getNumItems() > 1 ? bn(pswp.currIndex + 1) + " / " + bn(pswp.getNumItems()) : "";
      };
      pswp.on("change", update);
      update();
    },
  });
});

// The phone's back gesture closes the viewer instead of leaving the page.
lightbox.on("afterInit", () => history.pushState({ lpPhoto: true }, ""));
lightbox.on("close", () => {
  if (history.state && history.state.lpPhoto) history.back();
});
window.addEventListener("popstate", () => {
  if (lightbox.pswp) lightbox.pswp.close();
});

lightbox.init();

const groups = [
  document.querySelectorAll(".lp-hook img.lp-hero"),
  document.querySelectorAll("#lp-gallery img"),
  document.querySelectorAll(".lp-photos img"),
];

groups.forEach((nodes) => {
  const photos = Array.from(nodes);
  photos.forEach((img, index) => {
    img.classList.add("lp-zoomable");
    img.addEventListener("click", (event) => {
      event.preventDefault();
      const items = photos.map((photo) => {
        // The server gives each photo's real size; a still-loading photo falls back to its box.
        const width = Number(photo.dataset.zoomW) || photo.naturalWidth || 1200;
        const height = Number(photo.dataset.zoomH) || photo.naturalHeight || 900;
        return {
          src: photo.src.replace("/thumb-", "/"),
          msrc: photo.currentSrc || photo.src,
          width,
          height,
          alt: photo.alt,
          element: photo,
        };
      });
      lightbox.loadAndOpen(index, items);
    });
  });
});
