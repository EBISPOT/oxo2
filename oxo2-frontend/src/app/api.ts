export async function doHTTPRequest<T>(
    path: string,
    request?: RequestInit | undefined
): Promise<T> {
    // In production (when OXO_PUBLIC_URL is set), always use relative URLs through Caddy proxy
    // In local development (no OXO_PUBLIC_URL), use OXO_BACKEND_URL directly
    const backendUrl = import.meta.env.OXO_BACKEND_URL;
    const publicUrl = import.meta.env.OXO_PUBLIC_URL || '';
    
    let requestUrl: string;
    if (publicUrl && publicUrl !== '/') {
        // Production: use relative URL through Caddy proxy, prefixed with base path
        requestUrl = `${publicUrl}${path}`;
    } else if (backendUrl) {
        // Local development: use absolute URL directly to backend
        requestUrl = `${backendUrl}${path}`;
    } else {
        // Fallback: use relative URL (shouldn't happen in normal operation)
        requestUrl = path;
    }
    
    const response: Response = await fetch(
        requestUrl,
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
