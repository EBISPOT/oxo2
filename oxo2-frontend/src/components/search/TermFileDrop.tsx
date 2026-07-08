import { useCallback, useRef, useState } from "react";
import { parseTerms } from "../../util/terms";

/** Plain-text term lists only. Parsed in the browser; nothing is uploaded anywhere. */
const ACCEPTED_EXTENSIONS = [".txt", ".csv", ".tsv"];
/** A term list is tiny. Refuse anything that plainly isn't one rather than hanging the tab on it. */
const MAX_BYTES = 1024 * 1024;

function hasAcceptedExtension(filename: string): boolean {
    return ACCEPTED_EXTENSIONS.some((extension) => filename.toLowerCase().endsWith(extension));
}

/**
 * Drop (or pick) a file of terms, one per line. The file is read locally and its terms are appended to
 * the batch textarea, so what runs is always exactly what the user can see and edit — the file is an
 * input shortcut, never a hidden second source of truth.
 */
export function TermFileDrop({ onTerms }: { onTerms: (terms: string[]) => void }) {
    const inputRef = useRef<HTMLInputElement>(null);
    const [isDraggedOver, setIsDraggedOver] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const readFile = useCallback((file: File) => {
        if (!hasAcceptedExtension(file.name)) {
            setError(`${file.name} isn't a ${ACCEPTED_EXTENSIONS.join(", ")} file.`);
            return;
        }
        if (file.size > MAX_BYTES) {
            setError(`${file.name} is larger than 1 MB — that's not a term list.`);
            return;
        }
        file.text()
            .then((text) => {
                const terms = parseTerms(text);
                if (terms.length === 0) {
                    setError(`${file.name} has no terms in it.`);
                    return;
                }
                setError(null);
                onTerms(terms);
            })
            .catch(() => setError(`Could not read ${file.name}.`));
    }, [onTerms]);

    const handleDrop = useCallback((event: React.DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        setIsDraggedOver(false);
        const file = event.dataTransfer.files?.[0];
        if (file) readFile(file);
    }, [readFile]);

    return (
        <div>
            <div
                className={`rounded-md border-1 border-dashed p-3 text-center text-tertiary text-sm cursor-pointer transition-colors ${
                    isDraggedOver ? "border-link-default bg-orange-50" : "border-neutral-dark"
                }`}
                onDragOver={(event) => { event.preventDefault(); setIsDraggedOver(true); }}
                onDragLeave={() => setIsDraggedOver(false)}
                onDrop={handleDrop}
                onClick={() => inputRef.current?.click()}
                onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") inputRef.current?.click(); }}
                role="button"
                tabIndex={0}
            >
                Drop a file of terms here, or <span className="text-link-default underline">choose one</span>
                <div className="text-xs">One term per line. {ACCEPTED_EXTENSIONS.join(", ")}</div>
            </div>
            <input
                ref={inputRef}
                type="file"
                accept={ACCEPTED_EXTENSIONS.join(",")}
                className="hidden"
                onChange={(event) => {
                    const file = event.target.files?.[0];
                    if (file) readFile(file);
                    // Reset so choosing the same file twice fires change again.
                    event.target.value = "";
                }}
            />
            {error && <div className="text-sm mt-1 text-red-700" role="alert">{error}</div>}
        </div>
    );
}
