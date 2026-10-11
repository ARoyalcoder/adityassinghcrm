import { createContext, useContext } from 'react'
import type { CurrentUser } from '@/features/auth/api'

export type AuthState =
  /** Still finding out whether the refresh cookie holds a session. */
  | { status: 'loading'; user: null }
  | { status: 'anonymous'; user: null }
  | { status: 'authenticated'; user: CurrentUser }

export type AuthContextValue = AuthState & {
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return value
}

/**
 * Whether to SHOW something. This is a convenience for the user, not security: the backend checks
 * the same permission on every request and is the only thing that actually protects data.
 */
export function useHasPermission(permission: string): boolean {
  const { user } = useAuth()
  return user?.permissions.includes(permission) ?? false
}
