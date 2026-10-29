import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/lib/api'
import { returnTo, useAuth } from './auth-context'
import { useAuthConfig } from './auth-config'
import { GoogleButton } from './GoogleButton'

type Props = {
  remember: boolean
  text: 'signin_with' | 'signup_with'
}

/** "ou" + botão do Google no login/cadastro; conta nova vai direto ao perfil para escolher o @. */
export function GoogleSignIn({ remember, text }: Props) {
  const { data } = useAuthConfig()
  const { loginWithGoogle } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  async function signIn(credential: string) {
    try {
      const user = await loginWithGoogle(credential, remember)
      if (!user.profileComplete) {
        toast.success(`Bem-vindo(a), ${user.name.split(' ')[0]}! Escolha seu nome de usuário para continuar.`)
        navigate('/perfil', { replace: true })
      } else {
        navigate(returnTo(location.state), { replace: true })
      }
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível entrar com o Google.')
    }
  }

  if (!data?.googleClientId) return null
  return (
    <>
      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        <span className="h-px flex-1 bg-border" />
        ou
        <span className="h-px flex-1 bg-border" />
      </div>
      <GoogleButton onCredential={signIn} text={text} />
    </>
  )
}
