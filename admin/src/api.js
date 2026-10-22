/**
 * The console's only way to the backend (ARCHITECTURE §7, §14.11). Same origin, so the session cookie goes along
 * by itself. Every request is JSON, bodyless POSTs included, because the backend refuses anything else (D-133).
 */
export class ApiError extends Error {
  constructor(status, { code, message, details }) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details ?? [];
  }
}

async function request(method, path, body) {
  const hasBody = method !== "GET";
  const response = await fetch(path, {
    method,
    headers: hasBody ? { "content-type": "application/json" } : {},
    body: hasBody ? JSON.stringify(body ?? {}) : undefined,
  });
  if (response.status === 204) return null;
  const json = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(response.status, json.error ?? { code: "INTERNAL", message: `HTTP ${response.status}` });
  }
  return json;
}

export const login = (username, password) => request("POST", "/admin/login", { username, password });
export const logout = () => request("POST", "/admin/logout");
export const getPublished = () => request("GET", "/admin/config/published");
export const saveDraft = (document) => request("PUT", "/admin/config/draft", document);
export const publish = () => request("POST", "/admin/config/publish");

/** The saved draft, or null when there is none (the editor then starts from the published document). */
export async function getDraft() {
  try {
    return await request("GET", "/admin/config/draft");
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}
