#!/usr/bin/env bash
# Regenerate oxo2-user-interface.pdf from oxo2-user-interface.html.
#
# The HTML is the source of truth; the PDF is a build artifact. Edit the HTML, run this,
# and commit both. See README.md in this directory.
#
# Chromium/Chrome is the only renderer we depend on, because the page uses CSS the
# lighter converters (wkhtmltopdf, weasyprint) render badly: `break-*` fragmentation
# properties, `print-color-adjust: exact`, and inline SVG figures.
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source_html="${script_dir}/oxo2-user-interface.html"
output_pdf="${script_dir}/oxo2-user-interface.pdf"

browser=""
for candidate in google-chrome google-chrome-stable chromium chromium-browser; do
    if command -v "${candidate}" >/dev/null 2>&1; then
        browser="${candidate}"
        break
    fi
done

if [ -z "${browser}" ]; then
    echo "error: no Chrome or Chromium on PATH; cannot render the PDF." >&2
    echo "       Install one, or open ${source_html} and print to PDF by hand." >&2
    exit 1
fi

# --virtual-time-budget lets layout and font shaping settle before the snapshot;
# without it the first run occasionally captures a half-laid-out page.
"${browser}" \
    --headless \
    --disable-gpu \
    --no-sandbox \
    --no-pdf-header-footer \
    --virtual-time-budget=10000 \
    --print-to-pdf="${output_pdf}" \
    "file://${source_html}"

echo "Wrote ${output_pdf}"
