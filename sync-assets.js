"use strict";
// Keeps the local offline copy (android/app/src/main/assets/www) identical to
// the web sources at the project root. The root files remain the only place
// you need to edit.
const fs = require("fs");
const path = require("path");

const root = __dirname;
const dest = path.join(root, "android", "app", "src", "main", "assets", "www");
const sources = ["index.html", "privacy.html", "css", "js", "imgs", "webfonts"];

function copyAll() {
  fs.rmSync(dest, { recursive: true, force: true });
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of sources) {
    const from = path.join(root, entry);
    if (!fs.existsSync(from)) continue;
    const to = path.join(dest, entry);
    if (fs.statSync(from).isDirectory()) {
      fs.cpSync(from, to, { recursive: true, force: true });
    } else {
      fs.copyFileSync(from, to);
    }
  }
  console.log(`[sync] ${new Date().toLocaleTimeString()} -> ${dest}`);
}

copyAll();

if (process.argv.includes("--watch")) {
  let timer;
  const schedule = () => {
    clearTimeout(timer);
    timer = setTimeout(copyAll, 200);
  };
  for (const entry of sources) {
    const target = path.join(root, entry);
    if (!fs.existsSync(target)) continue;
    fs.watch(target, { recursive: true }, (_event, filename) => {
      if (filename) schedule();
    });
  }
  console.log("[watch] Watching for changes... (Ctrl+C to stop)");
}
