// GitHub Pages has no server-side rewrites, so a deep link like /transactions 404s on
// reload. Serving a copy of index.html as 404.html lets the React app boot and BrowserRouter
// take over from there. See https://github.com/rafgraph/spa-github-pages.
const fs = require("fs");
const path = require("path");

const distDir = path.join(__dirname, "..", "dist");
fs.copyFileSync(path.join(distDir, "index.html"), path.join(distDir, "404.html"));
