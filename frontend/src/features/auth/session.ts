import { setAccessToken, setUnauthorizedHandler } from '@/lib/api/client'
import { ApiError } from '@/lib/api/errors'
import { loginRequest, logoutRequest, refreshRequest, type CurrentUser } from '@/features/auth/api'

/**
 * The browser's session, outside React so the API client can use it.
 *
 * Refresh tokens are single-use: if two refreshes were sent with the same cookie, the server would
 * treat the second as a stolen token and end the session. So all callers share one in-flight refresh.
 */
type SessionListener = (user: CurrentUser | null) => void

const listeners = new Set<SessionListener>()
let refreshInFlight: Promise<CurrentUser | null> | null = null

function publish(user: CurrentUser | null) {
  listeners.forEach((listener) => listener(user))
}

/** Notified whenever the signed-in user changes, including when the session is lost in the background. */
export function subscribeToSession(listener: SessionListener) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Resolves to the user if the refresh cookie is still good, or null if the person must sign in. */
export function refreshSession(): Promise<CurrentUser | null> {
  refreshInFlight ??= refreshRequest()
    .then((tokens) => {
      setAccessToken(tokens.accessToken)
      publish(tokens.user)
      return tokens.user
    })
    .catch((error: unknown) => {
      // Only a definite "no" from the server ends the session; a network blip must not sign anyone out.
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        setAccessToken(null)
        publish(null)
        return null
      }
      throw error
    })
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

export async function signIn(email: string, password: string): Promise<CurrentUser> {
  const tokens = await loginRequest(email, password)
  setAccessToken(tokens.accessToken)
  publish(tokens.user)
  return tokens.user
}

export async function signOut(): Promise<void> {
  try {
    await logoutRequest()
  } finally {
    // Whatever the server said, this browser forgets the session.
    setAccessToken(null)
    publish(null)
  }
}

// When any API call gets a 401, try once to renew the access token; the client then repeats the call.
setUnauthorizedHandler(async () => {
  try {
    return (await refreshSession()) !== null
  } catch {
    return false
  }
})
