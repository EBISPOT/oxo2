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
#
# --generate-pdf-document-outline builds the PDF's bookmark tree from the heading structure, so
# readers can jump between sections from the viewer's sidebar instead of scrolling back to the
# in-page Contents. The in-page Contents stays: it is what the HTML version and any viewer without
# a sidebar rely on. Chrome ignores the flag if it does not recognise it, so an older browser still
# renders a correct PDF — just without bookmarks. Verify with: mutool show <pdf> outline
"${browser}" \
    --headless \
    --disable-gpu \
    --no-sandbox \
    --no-pdf-header-footer \
    --generate-pdf-document-outline \
    --virtual-time-budget=10000 \
    --print-to-pdf="${output_pdf}" \
    "file://${source_html}"

echo "Wrote ${output_pdf}"

# The in-app Documentation tab (/docs) serves the guide from the frontend's public/ directory. The
# frontend image builds with context ./oxo2-frontend and so cannot reach this directory at image
# build time — the served copy has to live inside the frontend and be committed. Refreshing it here
# makes "regenerate the PDF" and "update what /docs serves" the same action, so the copy cannot
# silently fall behind the source.
public_dir="$(cd -- "${script_dir}/../../.." && pwd)/oxo2-frontend/public"

if [ -d "${public_dir}" ]; then
    cp "${source_html}" "${output_pdf}" "${public_dir}/"
    echo "Refreshed the /docs copies in ${public_dir}"
else
    echo "warning: ${public_dir} not found; the /docs copies were NOT refreshed." >&2
fi
