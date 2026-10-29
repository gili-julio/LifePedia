import { Loader2Icon, UnlinkIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { api, ApiError } from '@/lib/api'
import { useAuth } from './auth-context'
import { useAuthConfig } from './auth-config'
import { GoogleButton } from './GoogleButton'
import type { User } from './types'

/** Vincular/desvincular a conta Google (entrar com um clique). Conta criada pelo Google não desvincula. */
export function GoogleAccount() {
  const { user, setUser } = useAuth()
  const { data } = useAuthConfig()
  const [busy, setBusy] = useState(false)
  if (!user || (!data?.googleClientId && !user.googleLinked)) return null

  async function run(request: () => Promise<User>, success: string) {
    setBusy(true)
    try {
      setUser(await request())
      toast.success(success)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível atualizar a conta Google.')
    } finally {
      setBusy(false)
    }
  }

  const link = (credential: string) =>
    run(() => api<User>('/api/users/me/google', { method: 'PUT', body: { credential } }), 'Conta Google vinculada.')
  const unlink = () => run(() => api<User>('/api/users/me/google', { method: 'DELETE' }), 'Conta Google desvinculada.')

  return (
    <Card>
      <CardHeader>
        <CardTitle>Conta Google</CardTitle>
        <CardDescription>
          {user.googleLinked
            ? user.hasPassword
              ? 'Vinculada: você pode entrar com o Google ou com e-mail e senha.'
              : 'Sua conta foi criada pelo Google e entra só por ele.'
            : 'Vincule para entrar com um clique, sem digitar a senha.'}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {user.googleLinked ? (
          user.hasPassword && (
            <Button variant="outline" disabled={busy} onClick={unlink}>
              {busy ? <Loader2Icon className="animate-spin" /> : <UnlinkIcon />} Desvincular
            </Button>
          )
        ) : busy ? (
          <Loader2Icon className="mx-auto animate-spin" />
        ) : (
          <div className="max-w-xs"><GoogleButton onCredential={link} text="continue_with" /></div>
        )}
      </CardContent>
    </Card>
  )
}
