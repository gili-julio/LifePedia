import { BellOffIcon, BellRingIcon, Loader2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { type PushState, useDevicePush } from './push'

const DESCRIPTION: Record<Exclude<PushState, 'loading' | 'off-server'>, string> = {
  unsupported: 'Este navegador não recebe notificações push. No celular, use o app instalado (menu do usuário → Instalar app).',
  install: 'No iPhone e no iPad, instale o app (menu do usuário → Instalar app) e ative por ele.',
  blocked: 'As notificações do LifePedia estão bloqueadas neste navegador. Libere nas configurações do site e volte aqui.',
  off: 'Receba lembretes de tarefas, avisos dos grupos e mensagens do chat mesmo com o app fechado.',
  on: 'Ativadas neste aparelho. Ao sair da conta, ele para de receber até você entrar de novo.',
}

/** Ativar/desativar o push no aparelho atual (cada aparelho é separado). */
export function PushCard() {
  const { state, busy, enable, disable } = useDevicePush()
  if (state === 'off-server') return null

  return (
    <Card>
      <CardHeader>
        <CardTitle>Notificações no aparelho</CardTitle>
        <CardDescription>{state === 'loading' ? 'Verificando este aparelho…' : DESCRIPTION[state]}</CardDescription>
      </CardHeader>
      {(state === 'off' || state === 'on') && (
        <CardContent>
          {state === 'off' ? (
            <Button disabled={busy} onClick={() => void enable()}>
              {busy ? <Loader2Icon className="animate-spin" /> : <BellRingIcon />} Ativar notificações
            </Button>
          ) : (
            <Button variant="outline" disabled={busy} onClick={() => void disable()}>
              {busy ? <Loader2Icon className="animate-spin" /> : <BellOffIcon />} Desativar neste aparelho
            </Button>
          )}
        </CardContent>
      )}
    </Card>
  )
}
