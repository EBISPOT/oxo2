export async function doHTTPRequest<T>(
    path: string,
    request?: RequestInit | undefined
): Promise<T> {
    const BASE_URL = import.meta.env.OXO_BACKEND_URL || 'http://localhost:8081';
    const response: Response = await fetch(
        `${BASE_URL}${path}`,
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
