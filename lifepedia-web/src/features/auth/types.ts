export type User = {
  id: string
  name: string
  /** Sem @; nulo em contas antigas até completarem o perfil */
  username: string | null
  email: string
  /** Nula sem foto; traz `?v=` (versão) */
  imageUrl: string | null
  createdAt: string
  /** false = falta dado obrigatório; o app leva ao perfil */
  profileComplete: boolean
  googleLinked: boolean
  /** false = conta criada pelo Google, entra só por ele */
  hasPassword: boolean
  /** Aceitou a versão vigente dos Termos e da Política (`LegalGate` pede quando false) */
  termsAccepted: boolean
}

/** O token fica no cookie, não no corpo. */
export type AuthResponse = {
  expiresAt: string
  /** "Lembrar de mim": cookie persistente */
  remember: boolean
  user: User
}

export type LoginRequest = {
  email: string
  password: string
  remember?: boolean
}

export type RegisterRequest = Omit<LoginRequest, 'remember'> & {
  name: string
  username: string
  /** Caixa "Li e aceito" do cadastro (obrigatória) */
  acceptTerms: boolean
}

/** Configuração pública da tela de login; sem `googleClientId` não há botão do Google. */
export type AuthConfig = { googleClientId: string | null }

export type ProfileRequest = { name: string; username: string }
