// The user guide is a standalone, self-contained document (docs/user/ui/oxo2-user-interface.html),
// copied into public/ by docs/user/ui/build.sh. It is shown in an iframe rather than injected into
// this page because it carries its own stylesheet for bare `body`, `h2`, `table` and `a` selectors,
// which would collide with the app's Tailwind styles. BASE_URL always ends in a slash.
const guideHtmlUrl = `${import.meta.env.BASE_URL}oxo2-user-interface.html`;
const guidePdfUrl = `${import.meta.env.BASE_URL}oxo2-user-interface.pdf`;

export default function Documentation() {
    return (
        <main className="container mx-auto">
            <div className="text-secondary">OxO Documentation</div>
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
