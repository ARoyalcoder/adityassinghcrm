import { z } from 'zod'
import { env } from '@/lib/env'
import { ApiError, apiProblemSchema, CLIENT_ERROR_CODES } from '@/lib/api/errors'

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
  /**
   * `false` for the endpoints that are called without an access token (login, refresh, logout,
   * password reset). Those must not send a stale token, and a 401 from them is final.
   */
  authenticated?: boolean
  /**
   * For a request that must take effect only once (recording a payment): a value made up by the
   * caller and sent again, unchanged, with every retry. The server answers a repeat with the result
   * of the first attempt instead of doing the work twice.
   */
  idempotencyKey?: string
}

const REQUEST_ID_HEADER = 'X-Request-Id'
const undefinedSchema = z.undefined()

// The access token lives in memory only: never in localStorage, where any injected script could read it.
// It is lost on a page reload and recovered from the HttpOnly refresh cookie (see features/auth/session.ts).
let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

/** Called when a request is rejected with 401; resolves to true if a new access token was obtained. */
type UnauthorizedHandler = () => Promise<boolean>
let unauthorizedHandler: UnauthorizedHandler | null = null

export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  unauthorizedHandler = handler
}

/**
 * The only place the app talks HTTP. Every response is validated against a Zod schema, and every
 * failure - server error body, network failure, unexpected payload - surfaces as an ApiError.
 * An expired access token is renewed once, transparently, and the request is repeated.
 */
export async function apiRequest<T>(path: string, schema: z.ZodType<T>, options: RequestOptions = {}): Promise<T> {
  const { authenticated = true } = options

  let response = await send(path, options)
  if (response.status === 401 && authenticated && unauthorizedHandler) {
    const renewed = await unauthorizedHandler()
    if (renewed) {
      response = await send(path, options)
    }
  }
  return parse(response, schema, options.method ?? 'GET', path)
}

/**
 * Fetches a file from the API (an export) with the same authentication and token renewal as
 * apiRequest, and hands it to the browser to save. A plain link cannot be used: it would not carry
 * the access token.
 */
export async function apiDownload(path: string, fallbackFileName: string): Promise<void> {
  let response = await send(path, {})
  if (response.status === 401 && unauthorizedHandler && (await unauthorizedHandler())) {
    response = await send(path, {})
  }
  if (!response.ok) {
    // Reuses the normal error handling: this always throws an ApiError.
    await parse(response, undefinedSchema, 'GET', path)
    return
  }
  const fileName = /filename="?([^";]+)"?/.exec(response.headers.get('Content-Disposition') ?? '')?.[1]
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = fileName ?? fallbackFileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

/**
 * Fetches something that is not JSON (an image) with the same authentication and token renewal as
 * apiRequest. Needed for pictures behind the API: an <img src> cannot carry the access token.
 */
export async function apiBlob(path: string, signal?: AbortSignal): Promise<Blob> {
  let response = await send(path, { signal })
  if (response.status === 401 && unauthorizedHandler && (await unauthorizedHandler())) {
    response = await send(path, { signal })
  }
  if (!response.ok) {
    // Reuses the normal error handling: this always throws an ApiError.
    await parse(response, undefinedSchema, 'GET', path)
  }
  return response.blob()
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const { method = 'GET', body, signal, authenticated = true, idempotencyKey } = options
  const upload = body instanceof FormData
  try {
    return await fetch(`${env.apiBaseUrl}${path}`, {
      method,
      signal,
      // Sends the refresh cookie to the /auth endpoints. The API is same-origin (proxied), so 'same-origin' is enough.
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json, application/problem+json, text/csv, image/*',
        // For a file upload the browser sets the multipart Content-Type (with its boundary) itself.
        ...(body !== undefined && !upload ? { 'Content-Type': 'application/json' } : {}),
        ...(authenticated && accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      },
      body: upload ? body : body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause
    }
    throw new ApiError({
      status: 0,
      code: CLIENT_ERROR_CODES.network,
      message: 'Could not reach the server. Check your connection and try again.',
    })
  }
}

async function parse<T>(response: Response, schema: z.ZodType<T>, method: string, path: string): Promise<T> {
  const requestId = response.headers.get(REQUEST_ID_HEADER) ?? undefined
  const hasBody = response.status !== 204 && response.status !== 202
  const payload: unknown = hasBody ? await readJson(response) : undefined

  if (!response.ok) {
    const problem = apiProblemSchema.safeParse(payload)
    if (problem.success) {
      throw ApiError.fromProblem({ ...problem.data, requestId: problem.data.requestId ?? requestId })
    }
    // e.g. an HTML error page from a proxy in front of the API
    throw new ApiError({
      status: response.status,
      code: CLIENT_ERROR_CODES.unexpectedResponse,
      message: `The server returned an unexpected error (HTTP ${response.status}).`,
      requestId,
    })
  }

  const result = schema.safeParse(payload)
  if (!result.success) {
    console.error(`Response from ${method} ${path} did not match its schema`, result.error.issues)
    throw new ApiError({
      status: response.status,
      code: CLIENT_ERROR_CODES.unexpectedResponse,
      message: 'The server returned data in an unexpected format.',
      requestId,
    })
  }
  return result.data
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json()
  } catch {
    return undefined
  }
}
