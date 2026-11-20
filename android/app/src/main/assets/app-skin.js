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
      "footer#contact,footer{display:none!important}",
      "@media(max-width:420px){.app-socials{gap:6px}.app-social{width:38px;height:38px;min-width:38px;flex-basis:38px}}",
      "@media(max-width:340px){.brand-logo{width:48px;height:48px;flex-basis:48px}",
      ".lang-toggle{width:44px;height:44px;min-width:44px;min-height:44px;flex-basis:44px}",
      ".app-social{width:34px;height:34px;min-width:34px;flex-basis:34px}}"
    ].join("");
    (document.head || document.documentElement).appendChild(style);
  }

  // 3) Put the three contact links between the logo and the language button.
  var actions = document.querySelector(".nav-actions");
  var lang = document.getElementById("langToggle");
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
