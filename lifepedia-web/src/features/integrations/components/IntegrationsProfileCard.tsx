import { ArrowRightIcon, PlugZapIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useIntegrationSummary } from '../api'

/** Atalho do perfil para a central de integrações, com quantas contas estão ligadas. */
export function IntegrationsProfileCard() {
  const { data } = useIntegrationSummary()
  const connected = data?.connected ?? 0
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><PlugZapIcon className="size-4" aria-hidden /> Integrações</CardTitle>
        <CardDescription>
          {!data ? 'Google Agenda, GitLab, GitHub e Duolingo.'
            : connected === 0 ? 'Nenhuma conta ligada. Ligue Google Agenda, GitLab, GitHub ou Duolingo.'
            : `${connected} ${connected === 1 ? 'conta ligada' : 'contas ligadas'}${data.problems ? `, ${data.problems} com problema` : ''}.`}
        </CardDescription>
        <CardAction>
          <Button asChild variant="ghost" size="sm"><Link to="/integracoes">Abrir <ArrowRightIcon /></Link></Button>
        </CardAction>
      </CardHeader>
    </Card>
  )
}
