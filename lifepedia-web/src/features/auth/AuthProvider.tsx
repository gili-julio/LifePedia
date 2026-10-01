import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError, setUnauthorizedListener } from '@/lib/api'
import { AuthContext, type AuthContextValue } from './auth-context'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from './types'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  /** Sessão expirada: limpa o estado local. */
  const clearSession = useCallback(() => {
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  const logout = useCallback(() => {
    api<void>('/api/auth/logout', { method: 'POST' }).catch(() => undefined)
    clearSession()
  }, [clearSession])

  useEffect(() => {
    setUnauthorizedListener(clearSession)
    return () => setUnauthorizedListener(null)
  }, [clearSession])

  useEffect(() => {
    api<User>('/api/users/me')
      .then(setUser)
      .catch((error) => {
        if (!(error instanceof ApiError && error.status === 401)) console.error(error)
      })
      .finally(() => setLoading(false))
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      logout,
      setUser,
      login: async (data: LoginRequest) =>
        setUser((await api<AuthResponse>('/api/auth/login', { method: 'POST', body: data })).user),
      register: async (data: RegisterRequest) =>
        setUser((await api<AuthResponse>('/api/auth/register', { method: 'POST', body: data })).user),
    }),
    [user, loading, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
