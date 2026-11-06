// Bat TV Shield — Content Script (run_at: document_start, all_frames: true)
// Se inyecta tanto en la página principal (doramasflix.io, dramachino.com, jkanime.net)
// como DENTRO de los iframes de los reproductores (VOE, Filemoon, Byse, Primeload, Streamwish, OK.ru).
(function () {
  const pageControlScript = function () {
    try {
      const dummyWin = {
        closed: false,
        focus: function () {},
        blur: function () {},
        close: function () {},
        postMessage: function () {},
        location: { href: "about:blank", assign: function () {}, replace: function () {} }
      };

      // 1. Congelar window.open en el contexto real de la página (wrappedJSObject / Main World)
      Object.defineProperty(window, "open", {
        value: function () {
          return dummyWin;
        },
        writable: false,
        configurable: false
      });

      // 2. Neutralizar navigator.webdriver para evitar falsos positivos de Cloudflare Turnstile
      try {
        Object.defineProperty(navigator, "webdriver", {
          get: function () {
            return false;
          },
          configurable: true
        });
      } catch (_) {}

      // 3. Eliminar capas invisibles de clickjacking sobre los reproductores
      const cleanClickjackOverlays = function () {
        const nodes = document.querySelectorAll("div, a, ins, iframe");
        for (let i = 0; i < nodes.length; i++) {
          const el = nodes[i];
          const style = window.getComputedStyle(el);
          const zIndex = parseInt(style.zIndex || "0", 10);
          if (
            (style.position === "fixed" || style.position === "absolute") &&
            zIndex > 999 &&
            (parseFloat(style.opacity || "1") < 0.12 ||
              (el.tagName === "A" && el.getAttribute("target") === "_blank"))
          ) {
            el.remove();
          }
        }
        // Forzar que ningún enlace abra ventana emergente
        const blankLinks = document.querySelectorAll('a[target="_blank"]');
        for (let j = 0; j < blankLinks.length; j++) {
          blankLinks[j].setAttribute("target", "_self");
          blankLinks[j].removeAttribute("onclick");
        }
      };

      document.addEventListener("DOMContentLoaded", cleanClickjackOverlays);
      document.addEventListener("click", cleanClickjackOverlays, true);
    } catch (_) {}
  };

  // Inyectar en el Main World de la página y de cada iframe reproductor
  try {
    const scriptEl = document.createElement("script");
    scriptEl.textContent = "(" + pageControlScript.toString() + ")();";
    (document.head || document.documentElement).appendChild(scriptEl);
    scriptEl.remove();
  } catch (_) {}
})();
