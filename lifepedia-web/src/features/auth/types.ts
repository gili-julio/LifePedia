export type User = {
  id: string
  name: string
  /** Sem @; nulo em contas antigas até completarem o perfil */
  username: string | null
  email: string
  createdAt: string
  /** false = falta dado obrigatório; o app leva ao perfil */
  profileComplete: boolean
}

/** O token fica no cookie, não no corpo. */
export type AuthResponse = {
  expiresAt: string
  user: User
}

export type LoginRequest = {
  email: string
  password: string
}

export type RegisterRequest = LoginRequest & {
  name: string
  username: string
}

export type ProfileRequest = { name: string; username: string }
