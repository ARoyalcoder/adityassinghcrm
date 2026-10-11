import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { AuthContext, type AuthContextValue, type AuthState } from '@/features/auth/auth-context'
import { refreshSession, signIn, signOut, subscribeToSession } from '@/features/auth/session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null })

  useEffect(() => {
    const unsubscribe = subscribeToSession((user) => {
      setState(user ? { status: 'authenticated', user } : { status: 'anonymous', user: null })
      if (!user) {
        // Nothing fetched for one user may be shown to the next.
        queryClient.clear()
      }
    })
    // A page load starts with no access token; the refresh cookie tells us if there is a session.
    refreshSession().catch(() => setState({ status: 'anonymous', user: null }))
    return unsubscribe
  }, [queryClient])

  const handleSignIn = useCallback(async (email: string, password: string) => {
    await signIn(email, password)
  }, [])

  const handleSignOut = useCallback(async () => {
    await signOut()
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ ...state, signIn: handleSignIn, signOut: handleSignOut }),
    [state, handleSignIn, handleSignOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
