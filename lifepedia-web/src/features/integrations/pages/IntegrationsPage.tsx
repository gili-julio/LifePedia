import { CheckIcon, PlugZapIcon, PlusIcon } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Skeleton } from '@/components/ui/skeleton'
import { useIntegrations } from '../api'
import { AccountRow } from '../components/AccountRow'
import { ConnectDialog } from '../components/ConnectDialog'
import { GOOGLE_ERRORS, PROVIDER_ORDER, PROVIDERS } from '../providers'
import type { IntegrationAccount, IntegrationProvider } from '../types'

/** Central de integrações: cada serviço com o que você ganha, o passo a passo e as contas ligadas. */
export function IntegrationsPage() {
  const { data } = useIntegrations()
  const [params, setParams] = useSearchParams()
  const [connecting, setConnecting] = useState<IntegrationProvider | null>(null)
  const [replacing, setReplacing] = useState<IntegrationAccount | null>(null)

  useEffect(() => {
    const error = params.get('erro')
    if (!params.get('conectado') && !error) return
    if (error) toast.error(GOOGLE_ERRORS[error] ?? GOOGLE_ERRORS.google)
    else toast.success('Google Agenda conectado. Os eventos aparecem em instantes.')
    setParams((current) => {
      current.delete('erro')
      current.delete('conectado')
      return current
    }, { replace: true })
  }, [params, setParams])

  const accounts = data?.accounts ?? []
  const problems = accounts.filter((a) => a.status === 'ERROR').length

  return (
    <div className="space-y-6">
      <PageHeader title="Integrações"
        description="Ligue suas contas de fora (agenda, GitLab, GitHub, Duolingo) e veja tudo no LifePedia." />

      {!data ? <Skeleton className="h-64" /> : (
        <>
          <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground" aria-live="polite">
            <PlugZapIcon className="size-4" aria-hidden />
            {accounts.length === 0 ? 'Nenhuma conta ligada ainda.'
              : `${accounts.length} ${accounts.length === 1 ? 'conta ligada' : 'contas ligadas'}`}
            {problems > 0 && <Badge variant="destructive">{problems} {problems === 1 ? 'precisa' : 'precisam'} de atenção</Badge>}
          </p>

          <ul className="grid gap-4 lg:grid-cols-2">
            {PROVIDER_ORDER.map((provider) => {
              const meta = PROVIDERS[provider]
              const info = data.providers.find((p) => p.provider === provider)
              const mine = accounts.filter((a) => a.provider === provider)
              const canAdd = !!info?.available && (meta.multiple || mine.length === 0)
              return (
                <li key={provider}>
                  <Card className="h-full">
                    <CardHeader className="gap-3">
                      <div className="flex items-start gap-3">
                        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-muted">
                          <meta.icon className="size-5" aria-hidden />
                        </span>
                        <div className="min-w-0 flex-1 space-y-1">
                          <CardTitle className="flex flex-wrap items-center gap-2">
                            {meta.label}
                            {mine.length > 0 ? <Badge variant="secondary">{mine.length} {mine.length === 1 ? 'conta' : 'contas'}</Badge>
                              : !info?.available ? <Badge variant="outline">Indisponível</Badge> : null}
                          </CardTitle>
                          <CardDescription>{meta.description}</CardDescription>
                        </div>
                      </div>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div className="space-y-1.5">
                        <p className="text-xs font-medium text-muted-foreground">O que você ganha</p>
                        <ul className="space-y-1">
                          {meta.benefits.map((b) => (
                            <li key={b} className="flex items-start gap-2 text-sm">
                              <CheckIcon className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />{b}
                            </li>
                          ))}
                        </ul>
                      </div>

                      {mine.length > 0 && (
                        <ul className="divide-y rounded-xl border px-3">
                          {mine.map((account) => <AccountRow key={account.id} account={account} onReconnect={setReplacing} />)}
                        </ul>
                      )}

                      {!info?.available && info?.unavailable && (
                        <p className="rounded-xl bg-muted px-3 py-2 text-xs text-muted-foreground">{info.unavailable}</p>
                      )}

                      <Collapsible className="space-y-2">
                        <div className="flex flex-wrap items-center gap-2">
                          {canAdd && (
                            <Button size="sm" variant={mine.length ? 'outline' : 'default'} onClick={() => setConnecting(provider)}>
                              <PlusIcon /> {mine.length ? 'Conectar outra conta' : 'Conectar'}
                            </Button>
                          )}
                          <CollapsibleTrigger asChild>
                            <Button size="sm" variant="ghost">Como conectar</Button>
                          </CollapsibleTrigger>
                        </div>
                        <CollapsibleContent>
                          <ol className="list-decimal space-y-1 pl-5 text-sm text-muted-foreground">
                            {meta.steps.map((step) => <li key={step}>{step}</li>)}
                          </ol>
                        </CollapsibleContent>
                      </Collapsible>
                    </CardContent>
                  </Card>
                </li>
              )
            })}
          </ul>
        </>
      )}

      <ConnectDialog provider={replacing ? replacing.provider : connecting} account={replacing}
        onOpenChange={(open) => {
          if (open) return
          setConnecting(null)
          setReplacing(null)
        }} />
    </div>
  )
}
