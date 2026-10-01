import { createContext, useContext } from 'react'
import type { LoginRequest, RegisterRequest, User } from './types'

export type AuthContextValue = {
  user: User | null
  /** true enquanto verifica a sessão */
  loading: boolean
  login: (data: LoginRequest) => Promise<void>
  register: (data: RegisterRequest) => Promise<void>
  logout: () => void
  /** Atualiza o usuário após editar o perfil. */
  setUser: (user: User) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth precisa estar dentro de <AuthProvider>')
  return context
}
