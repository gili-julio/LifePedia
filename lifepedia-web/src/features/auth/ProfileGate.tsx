import { UserRoundPenIcon } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogMedia,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { useAuth } from './auth-context'

/** Cadastro incompleto: avisa e leva ao perfil (bloqueia o resto do app até completar). */
export function ProfileGate() {
  const { user } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const open = !!user && !user.profileComplete && pathname !== '/perfil'

  return (
    <AlertDialog open={open}>
      <AlertDialogContent onEscapeKeyDown={(e) => e.preventDefault()}>
        <AlertDialogHeader>
          <AlertDialogMedia>
            <UserRoundPenIcon />
          </AlertDialogMedia>
          <AlertDialogTitle>Atualize suas informações cadastrais</AlertDialogTitle>
          <AlertDialogDescription>
            Algumas informações obrigatórias estão faltando, como o seu nome de usuário (@). Complete o cadastro para
            continuar usando o LifePedia.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogAction onClick={() => navigate('/perfil')}>Atualizar cadastro</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
