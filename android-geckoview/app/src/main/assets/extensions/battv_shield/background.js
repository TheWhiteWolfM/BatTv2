// Bat TV Shield — Background WebRequest Interceptor (GeckoView 132.0)
(function () {
  const BLOCKED_HOSTS = [
    "popads.net",
    "propellerads.com",
    "exoclick.com",
    "adsterra.com",
    "clickadu.com",
    "hilltopads.net",
    "galaksion.com",
    "juicyads.com",
    "trafficjunky.net",
    "onclickads.net",
    "blurtsbrerd.com",
    "aarems.org",
    "21wiz.com",
    "capsidsflyleaf.com",
    "skypipevatican.com",
    "1xbet.com",
    "bet365.com"
  ];

  // 1. Interceptar y cancelar peticiones de red hacia servidores de anuncios y pop-unders
  if (typeof browser !== "undefined" && browser.webRequest && browser.webRequest.onBeforeRequest) {
    browser.webRequest.onBeforeRequest.addListener(
      function (details) {
        try {
          const url = new URL(details.url);
          const host = url.hostname.toLowerCase();
          for (let i = 0; i < BLOCKED_HOSTS.length; i++) {
            const pattern = BLOCKED_HOSTS[i];
            if (host === pattern || host.endsWith("." + pattern)) {
              return { cancel: true };
            }
          }
          // Desacortar automáticamente embedshortener.co/e/<jwt> en peticiones de sub_frame
          if (host.includes("embedshortener.co") && url.pathname.startsWith("/e/")) {
            const token = url.pathname.slice(3).split(".")[1];
            if (token) {
              const payload = JSON.parse(atob(token.replace(/-/g, "+").replace(/_/g, "/")));
              if (payload && payload.link) {
                const decodedUrl = atob(payload.link).trim();
                if (/^https?:\/\//i.test(decodedUrl)) {
                  return { redirectUrl: decodedUrl };
                }
              }
            }
          }
        } catch (_) {}
        return {};
      },
      { urls: ["<all_urls>"] },
      ["blocking"]
    );
  }

  // 2. Cerrar instantáneamente cualquier pestaña emergente si logra crearse
  if (typeof browser !== "undefined" && browser.tabs && browser.tabs.onCreated) {
    browser.tabs.onCreated.addListener(function (tab) {
      if (tab && tab.openerTabId !== undefined && tab.id !== undefined) {
        browser.tabs.remove(tab.id).catch(function () {});
      }
    });
  }
})();
