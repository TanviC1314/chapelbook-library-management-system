import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, type RegisterInput } from '../api/auth'
import { setUnauthorizedHandler } from '../api/client'
import type { AuthResponse, User } from '../api/types'
import { isTokenExpired } from '../lib/jwt'
import { tokenStore } from './tokenStore'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthState {
  status: AuthStatus
  user: User | null
  login: (email: string, password: string) => Promise<User>
  register: (input: RegisterInput) => Promise<User>
  logout: () => void
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthState | null>(null)

/** A stored token that has not yet expired means we should try to restore the session; otherwise forget it. */
function hasUsableStoredToken(): boolean {
  const token = tokenStore.get()
  if (token && !isTokenExpired(token)) return true
  tokenStore.clear()
  return false
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AuthStatus>(() => (hasUsableStoredToken() ? 'loading' : 'anonymous'))
  const [user, setUser] = useState<User | null>(null)

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
    setStatus('anonymous')
    // Never leave one person's cached data around for the next person who signs in.
    queryClient.clear()
  }, [queryClient])

  // A request the server rejects as unauthenticated (expired token, deactivated account) ends the session.
  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  // Restore the session after a page reload.
  useEffect(() => {
    if (status !== 'loading') return
    let cancelled = false
    authApi
      .me()
      .then((me) => {
        if (cancelled) return
        setUser(me)
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        tokenStore.clear()
        setStatus('anonymous')
      })
    return () => {
      cancelled = true
    }
    // Runs once on mount: later status changes come from login and logout, not from restoring.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const signIn = useCallback((response: AuthResponse) => {
    tokenStore.set(response.accessToken)
    setUser(response.user)
    setStatus('authenticated')
    return response.user
  }, [])

  const login = useCallback(
    async (email: string, password: string) => signIn(await authApi.login({ email, password })),
    [signIn],
  )

  // The backend signs a new student in as part of registering, so there is no second round trip.
  const register = useCallback(async (input: RegisterInput) => signIn(await authApi.register(input)), [signIn])

  const value = useMemo(() => ({ status, user, login, register, logout }), [status, user, login, register, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

/** The signed-in user. Only call this below ProtectedRoute, where a user is guaranteed to exist. */
// eslint-disable-next-line react-refresh/only-export-components
export function useCurrentUser(): User {
  const { user } = useAuth()
  if (!user) throw new Error('useCurrentUser needs a signed-in user')
  return user
}
