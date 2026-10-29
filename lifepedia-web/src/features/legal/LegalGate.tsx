import { Loader2Icon, ScrollTextIcon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogMedia,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/auth-context'
import type { User } from '@/features/auth/types'
import { api, ApiError } from '@/lib/api'
import { LEGAL_VERSION } from './legal'

/** Conta sem aceite da versão vigente (criada pelo Google ou termos atualizados): bloqueia o app até aceitar. */
export function LegalGate() {
  const { user, setUser, logout } = useAuth()
  const [busy, setBusy] = useState(false)
  const open = !!user && !user.termsAccepted

  async function accept() {
    setBusy(true)
    try {
      setUser(await api<User>('/api/users/me/terms', { method: 'PUT', body: { version: LEGAL_VERSION } }))
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível registrar o aceite.')
    } finally {
      setBusy(false)
    }
  }

  const link = 'font-medium text-foreground underline underline-offset-4'
  return (
    <AlertDialog open={open}>
      <AlertDialogContent onEscapeKeyDown={(e) => e.preventDefault()}>
        <AlertDialogHeader>
          <AlertDialogMedia>
            <ScrollTextIcon />
          </AlertDialogMedia>
          <AlertDialogTitle>Termos de uso e privacidade</AlertDialogTitle>
          <AlertDialogDescription>
            Para continuar usando o LifePedia, leia e aceite os{' '}
            <Link to="/termos" target="_blank" rel="noopener" className={link}>Termos de uso</Link> e a{' '}
            <Link to="/privacidade" target="_blank" rel="noopener" className={link}>Política de privacidade</Link>.
            Eles explicam como seus dados são usados e protegidos.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <Button variant="outline" disabled={busy} onClick={logout}>Sair</Button>
          <Button disabled={busy} onClick={() => void accept()}>
            {busy && <Loader2Icon className="animate-spin" />} Li e aceito
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
