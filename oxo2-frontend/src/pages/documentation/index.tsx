import { resolveUrl } from "../../app/api";

// The user guide is a standalone, self-contained document (docs/user/ui/oxo2-user-interface.html),
// copied into public/ by docs/user/ui/build.sh. It is shown in an iframe rather than injected into
// this page because it carries its own stylesheet for bare `body`, `h2`, `table` and `a` selectors,
// which would collide with the app's Tailwind styles. BASE_URL always ends in a slash.
const guideHtmlUrl = `${import.meta.env.BASE_URL}oxo2-user-interface.html`;
const guidePdfUrl = `${import.meta.env.BASE_URL}oxo2-user-interface.pdf`;

// Swagger UI and the OpenAPI spec are served by the BACKEND, not out of the frontend's public/
// directory, so they resolve like an API call and not from BASE_URL. That keeps them correct in
// every environment without naming any of them: deployed, the backend shares the site's public URL
// (OXO_PUBLIC_URL) and these come out relative to it; in local development the backend is a
// separate origin and OXO_BACKEND_URL makes them absolute against it. A new deployment needs no
// change here — but its ingress does need to route <public URL>/swagger-ui* and
// <public URL>/v3/api-docs to the backend, as only the API prefix reaches it by default.
const swaggerUiUrl = resolveUrl("/swagger-ui.html");
const openApiSpecUrl = resolveUrl("/v3/api-docs");

export default function Documentation() {
    return (
        <main className="container mx-auto">
            <div className="text-secondary">OxO API Documentation</div>
            <div className="text-tertiary">
                <p className="mb-4">
                    The REST API describes itself. Read the endpoints, their parameters and their
                    responses — and call them against this instance — in&thinsp;
                    <a
                        href={swaggerUiUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="link-default"
                    >
                        Swagger UI
                    </a>
                    , or take the raw&thinsp;
                    <a
                        href={openApiSpecUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="link-default"
                    >
                        OpenAPI 3 specification
                    </a>
                    &thinsp;to generate a client. There is no write API: mappings are populated by
                    the offline dataload pipeline.
                </p>
            </div>

            <div className="text-secondary mt-8">OxO UI Documentation</div>
            <div className="text-tertiary">
                <p className="mb-4">
                    A guide to this interface: searching for mappings, reading the results, and
                    understanding how they are ordered.&thinsp;
                    <a
                        href={guideHtmlUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="link-default"
                    >
                        Open it in a new tab
                    </a>
                    &thinsp;for more room, or&thinsp;
                    <a
                        href={guidePdfUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="link-default"
                    >
                        download the PDF
                    </a>
                    &thinsp;to print it.
                </p>
            </div>
            <iframe
                src={guideHtmlUrl}
                title="Using the OxO mapping service"
                className="mb-8 h-[calc(100vh-18rem)] min-h-[30rem] w-full rounded border border-neutral-300"
            />
        </main>
    );
}
