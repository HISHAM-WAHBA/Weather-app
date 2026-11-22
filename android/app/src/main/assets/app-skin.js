/*
 * App-only visual tweaks. Injected into the live page by MainActivity, so the
 * website stays the source of truth and the changes never appear on Vercel.
 */
(function () {
  "use strict";

  // 1) Remove the footer completely inside the app.
  var footer = document.getElementById("contact") || document.querySelector("footer");
  if (footer && footer.parentNode) footer.parentNode.removeChild(footer);

  // 2) Styles for the contact icons that live in the navbar (app only).
  if (!document.getElementById("appSkinStyle")) {
    var style = document.createElement("style");
    style.id = "appSkinStyle";
    style.textContent = [
      ".app-socials{display:flex;align-items:center;gap:8px;margin-inline-end:6px}",
      ".app-social{width:42px;height:42px;min-width:42px;flex:0 0 42px;box-sizing:border-box;",
      "padding:0;border-radius:50%;display:grid;place-items:center;",
      "text-decoration:none;border:1px solid rgba(56,189,248,.35);background:rgba(56,189,248,.1);",
      "transition:transform .25s ease,background .25s ease,border-color .25s ease}",
      ".app-social i{color:#67e8f9;font-size:16px;line-height:1}",
      ".app-social:hover{background:rgba(56,189,248,.2);border-color:rgba(56,189,248,.65)}",
      ".app-social:active{transform:scale(.92)}",
      ".app-bell{width:42px;height:42px;min-width:42px;flex:0 0 42px;box-sizing:border-box;",
      "padding:0;border-radius:50%;display:grid;place-items:center;cursor:pointer;color:#67e8f9;",
      "border:1px solid rgba(56,189,248,.35);background:rgba(56,189,248,.1);",
      "transition:transform .25s ease,background .25s ease,border-color .25s ease}",
      ".app-bell i{font-size:16px;line-height:1}",
      ".app-bell.is-on{background:rgba(56,189,248,.26);border-color:rgba(56,189,248,.8)}",
      ".app-bell:active{transform:scale(.92)}",
      "footer#contact,footer{display:none!important}",
      "html,body{scrollbar-width:none!important;-ms-overflow-style:none!important}",
      "html::-webkit-scrollbar,body::-webkit-scrollbar{width:0!important;height:0!important;display:none!important}",
      "body{padding-bottom:28px!important}",
      "#appVersionBadge{position:fixed;left:0;right:0;bottom:6px;z-index:9999;text-align:center;",
      "font:500 11px/1.2 Poppins,sans-serif;letter-spacing:.4px;color:rgba(165,180,207,.78);",
      "text-shadow:0 1px 4px rgba(0,0,0,.75);pointer-events:none;user-select:none}",
      "@media(max-width:420px){.app-socials{gap:6px}.app-social,.app-bell{width:38px;height:38px;min-width:38px;flex-basis:38px}}",
      "@media(max-width:340px){.brand-logo{width:48px;height:48px;flex-basis:48px}",
      ".lang-toggle{width:44px;height:44px;min-width:44px;min-height:44px;flex-basis:44px}",
      ".app-social,.app-bell{width:34px;height:34px;min-width:34px;flex-basis:34px}}"
    ].join("");
    (document.head || document.documentElement).appendChild(style);
  }

  // 2b) Version name pinned to the very bottom of the app (never the website).
  var version = String(window.APP_VERSION || "").trim();
  if (version && !document.getElementById("appVersionBadge")) {
    var badge = document.createElement("div");
    badge.id = "appVersionBadge";
    badge.textContent = "weather app v" + version;
    (document.body || document.documentElement).appendChild(badge);
  }

  // 3) App-only bell button: turns the 7 AM / 10 PM weather alerts on or off.
  var actions = document.querySelector(".nav-actions");
  var lang = document.getElementById("langToggle");
  var bridge = window.WeatherBridge;
  if (actions && lang && bridge && !document.getElementById("appBell")) {
    var bell = document.createElement("button");
    bell.id = "appBell";
    bell.type = "button";
    bell.className = "app-bell";
    actions.insertBefore(bell, lang);

    function paintBell() {
      var on = false;
      try { on = !!bridge.getNotificationsEnabled(); } catch (e) {}
      bell.classList.toggle("is-on", on);
      bell.innerHTML = on
        ? '<i class="fa-solid fa-bell" aria-hidden="true"></i>'
        : '<i class="fa-solid fa-bell-slash" aria-hidden="true"></i>';
      bell.setAttribute("aria-label", on ? "Notifications on" : "Notifications off");
      bell.title = on ? "Notifications on · tap to stop" : "Notifications off · tap to start";
    }
    paintBell();
    bell.addEventListener("click", function () {
      try {
        bridge.setNotificationsEnabled(!bridge.getNotificationsEnabled());
        setTimeout(paintBell, 700);
      } catch (e) {}
    });
  }

  // 4) Put the three contact links between the bell and the language button.
  if (!actions || !lang || document.getElementById("appSocials")) return;

  var wrap = document.createElement("div");
  wrap.id = "appSocials";
  wrap.className = "app-socials";
  wrap.innerHTML = [
    '<a class="app-social" href="mailto:hishamwahba7@gmail.com" aria-label="Email" title="Email">',
    '<i class="fa-solid fa-envelope" aria-hidden="true"></i></a>',
    '<a class="app-social" href="https://wa.me/201553579114" target="_blank" rel="noopener noreferrer" aria-label="WhatsApp" title="WhatsApp">',
    '<i class="fa-brands fa-whatsapp" aria-hidden="true"></i></a>',
    '<a class="app-social" href="https://www.facebook.com/HISHAM.WAHBA.77" target="_blank" rel="noopener noreferrer" aria-label="Facebook" title="Facebook">',
    '<i class="fa-brands fa-facebook-f" aria-hidden="true"></i></a>'
  ].join("");
  actions.insertBefore(wrap, lang);
})();
