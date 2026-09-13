/*
 * Everything the app injects into Tesco's site, which is now only the cookie
 * banner. The previous version drove the Add button through guessed CSS
 * selectors; that is gone, because the person does the adding.
 *
 * What remains depends on nothing but the banner's own id, and if it stops
 * matching, the only cost is that you dismiss the banner yourself.
 */
(function () {
  if (window.__pantryConsent) return;
  window.__pantryConsent = true;

  var CONSENT_SELECTORS = [
    '#onetrust-accept-btn-handler',
    'button[id*="accept-all"]',
    'button[data-auto="accept-cookies"]'
  ];

  function dismiss() {
    for (var i = 0; i < CONSENT_SELECTORS.length; i++) {
      var el = document.querySelector(CONSENT_SELECTORS[i]);
      if (el) { el.click(); return true; }
    }
    return false;
  }

  /* The banner is injected after load, so try for a few seconds then give up. */
  if (dismiss()) return;
  var started = Date.now();
  var timer = setInterval(function () {
    if (dismiss() || Date.now() - started > 6000) clearInterval(timer);
  }, 300);
})();
