export async function http<T>(
  path: string,
  request?: RequestInit | undefined
): Promise<T> {
  const response: Response = await fetch(path, {
    ...(request ? request : {}),
  });

  if (!response.ok) {
    const message = `Failure loading ${response.url} with status ${response.status} (${response.statusText})`;
    console.dir(message);
    throw new Error(message);
  }

  return await response.json();
}

export async function get<TRes>(path: string): Promise<TRes> {
  return await http<TRes>(path);
}

export async function post<TReq, TRes>(
  path: string,
  body: TReq
): Promise<TRes> {
  return await http<TRes>(path, {
    method: "POST",
    body: JSON.stringify(body),
    headers: {
      "content-type": "application/json",
    },
  });
}

export async function put<TReq, TRes>(path: string, body: TReq): Promise<TRes> {
  return await http<TRes>(path, {
    method: "PUT",
    body: JSON.stringify(body),
    headers: {
      "content-type": "application/json",
    },
  });
}
