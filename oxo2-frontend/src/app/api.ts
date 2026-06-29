// Resolve a backend path to a full request URL.
// In production (OXO_PUBLIC_URL set) use relative URLs through the Caddy proxy; in local development
// (OXO_BACKEND_URL set) hit the backend directly; otherwise fall back to a relative URL.
function resolveUrl(path: string): string {
    const backendUrl = import.meta.env.OXO_BACKEND_URL;
    const publicUrl = import.meta.env.OXO_PUBLIC_URL || '';
    if (publicUrl && publicUrl !== '/') {
        return `${publicUrl}${path}`;
    }
    if (backendUrl) {
        return `${backendUrl}${path}`;
    }
    return path;
}

export async function doHTTPRequest<T>(
    path: string,
    request?: RequestInit | undefined
): Promise<T> {
    const response: Response = await fetch(
        resolveUrl(path),
        {
            ...(request ? request : {}),
        }
    );

    if (!response.ok) {
        const message = `Failure loading ${response.url} with status ${response.status} (${response.statusText})`;
        console.dir(message);
        throw new Error(message);
    }

    return await response.json();
}

export async function get<TRes>(path: string): Promise<TRes> {
    return await doHTTPRequest<TRes>(path);
}

export async function post<TReq, TRes>(path: string,
    body: TReq
): Promise<TRes> {
    return await doHTTPRequest<TRes>(path, {
        method: "POST",
        body: JSON.stringify(body),
        headers: {
            "content-type": "application/json",
        },
    });
}

/**
 * POST a JSON body to a streaming export endpoint (ADR-0024) and save the response as a file. Used for
 * the SSSOM-TSV export, whose POST + body can't be a plain download link.
 */
export async function downloadPost<TReq>(path: string, body: TReq, filename: string): Promise<void> {
    const response = await fetch(resolveUrl(path), {
        method: "POST",
        body: JSON.stringify(body),
        headers: { "content-type": "application/json" },
    });
    if (!response.ok) {
        throw new Error(`Export failed: ${response.status} (${response.statusText})`);
    }
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
}
