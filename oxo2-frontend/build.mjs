import { exec } from "child_process";
import { build } from "esbuild";
import fs from "fs";
import path from 'path';

let define = {};
for (const k in process.env) {
  define[`process.env.${k}`] = JSON.stringify(process.env[k]);
}

// Ensure the dist directory exists
const distDir = path.resolve('dist');
if (!fs.existsSync(distDir)) {
  fs.mkdirSync(distDir, { recursive: true });
}

///
/// Build index.html (simple find and replace)
///
console.log("### Building index.html");
fs.writeFileSync(
  "dist/index.html",
  fs
    .readFileSync("index.html.in")
    .toString()
    .split("%PUBLIC_URL%/")
    .join(process.env.PUBLIC_URL || "/")
    .split("%PUBLIC_URL%")
    .join(process.env.PUBLIC_URL || "/")
);

///
/// Build bundle.js (esbuild)
///
console.log("### Building bundle.js");
build({
  entryPoints: ["src/index.tsx"],
  bundle: true,
  platform: "browser",
  outfile: "dist/bundle.js",
  define,
  plugins: [],
  logLevel: "info",
  sourcemap: "linked",

  ...(process.env.OXO_MINIFY === "true"
    ? {
        minify: true,
      }
    : {}),
});

///
/// Build styles.css (tailwind)
///
console.log("### Building styles.css");
exec("tailwind -i ./src/index.css -o ./dist/styles.css");

///
/// Copy files
///
console.log("### Copying misc files");
exec("cp ./public/* ./dist");

