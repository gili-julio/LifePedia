import {
  ArchiveIcon,
  ArchiveRestoreIcon,
  ArrowLeftIcon,
  BanknoteIcon,
  ClockIcon,
  CopyIcon,
  ExternalLinkIcon,
  PencilIcon,
  RefreshCwIcon,
  Trash2Icon,
} from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatInstantDate, formatRelative, formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useArchiveDelivery, useDeleteDelivery, useDelivery, useRefreshDelivery } from '../api'
import { DeliveryBadges, DeliveryThumb } from '../components/DeliveryCard'
import { DeliveryDialog } from '../components/DeliveryDialog'
import { StageBar } from '../components/StageBar'
import { addressLine, forecast, STALL_DAYS, STATUS_LABEL } from '../labels'

const when = (iso: string) => `${formatInstantDate(iso)} ${formatTime(iso)}`

/** Detalhe da entrega: etapas, prazo, alertas (taxa, parada), endereço e todos os eventos do rastreio. */
export function DeliveryPage() {
  const { deliveryId = '' } = useParams()
  const navigate = useNavigate()
  const { data, isError, error } = useDelivery(deliveryId)
  const refresh = useRefreshDelivery()
  const archive = useArchiveDelivery()
  const remove = useDeleteDelivery()
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)

  if (isError) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-destructive">{error instanceof ApiError ? error.message : 'Não foi possível abrir a entrega.'}</p>
        <Button variant="outline" asChild><Link to="/entregas"><ArrowLeftIcon /> Voltar para Entregas</Link></Button>
      </div>
    )
  }
  if (!data) return <Skeleton className="h-96" />

  const d = data
  const eta = forecast(d)
  const stalled = d.stalledDays != null && d.stalledDays >= STALL_DAYS

  async function run(action: () => Promise<unknown>, done: string) {
    try {
      await action()
      toast.success(done)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível concluir.')
    }
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(d.trackingCode)
      toast.success('Código copiado.')
    } catch {
      toast.error('Não foi possível copiar.')
    }
  }

  const actions = [
    ...(d.tracking ? [{ label: 'Atualizar agora', icon: RefreshCwIcon, primary: true,
      onSelect: () => void run(() => refresh.mutateAsync(d.id), 'Rastreio atualizado.') }] : []),
    ...(d.mine ? [
      { label: 'Editar', icon: PencilIcon, onSelect: () => setEditing(true) },
      d.archivedAt
        ? { label: 'Voltar para em andamento', icon: ArchiveRestoreIcon, secondary: true,
          onSelect: () => void run(() => archive.mutateAsync({ id: d.id, archived: false }), 'Entrega de volta em andamento.') }
        : { label: 'Arquivar', icon: ArchiveIcon, secondary: true,
          onSelect: () => void run(() => archive.mutateAsync({ id: d.id, archived: true }), 'Entrega arquivada.') },
      { label: 'Excluir', icon: Trash2Icon, secondary: true, destructive: true, onSelect: () => setDeleting(true) },
    ] : []),
  ]

  return (
    <div className="space-y-6">
      <Link to="/entregas" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeftIcon className="size-4" aria-hidden /> Entregas
      </Link>
      <PageHeader title={d.name} description={STATUS_LABEL[d.status]} actions={actions} />

      <Card>
        <CardContent className="space-y-5">
          <div className="flex items-start gap-4">
            <DeliveryThumb delivery={d} className="size-16 sm:size-20" />
            <div className="min-w-0 flex-1 space-y-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-mono text-sm tracking-wider break-all">{d.trackingCode}</span>
                <Button size="icon-sm" variant="ghost" aria-label="Copiar código" onClick={() => void copy()}><CopyIcon /></Button>
              </div>
              {d.carrier && (
                <p className="text-sm text-muted-foreground">
                  {d.carrier.url ? (
                    <a href={d.carrier.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 underline-offset-2 hover:underline">
                      {d.carrier.name} <ExternalLinkIcon className="size-3.5" aria-hidden />
                    </a>
                  ) : d.carrier.name}
                </p>
              )}
              <DeliveryBadges delivery={d} />
            </div>
          </div>
          <StageBar delivery={d} />
          <dl className="grid grid-cols-2 gap-3 text-sm xl:grid-cols-4">
            <Info label={d.status === 'DELIVERED' ? 'Entregue em' : 'Previsão'}>
              {d.status === 'DELIVERED' && d.deliveredAt ? when(d.deliveredAt) : eta ?? 'Sem previsão da transportadora'}
            </Info>
            <Info label="Tempo em trânsito">{d.daysInTransit != null ? `${d.daysInTransit} ${d.daysInTransit === 1 ? 'dia' : 'dias'}` : '-'}</Info>
            <Info label="Última novidade">{d.latestAt ? formatRelative(d.latestAt) : 'Nenhuma ainda'}</Info>
            <Info label="Origem">{d.origin ?? '-'}</Info>
          </dl>
          {d.syncError && <p className="text-xs text-destructive">Última consulta falhou: {d.syncError}</p>}
          {!d.tracking && <p className="text-xs text-muted-foreground">Rastreio automático desligado no servidor: confira no site da transportadora.</p>}
          {d.lastSyncAt && <p className="text-xs text-muted-foreground">Consultado {formatRelative(d.lastSyncAt)}.</p>}
        </CardContent>
      </Card>

      {d.taxPending && (
        <Alert icon={BanknoteIcon} title="Aguardando pagamento de imposto ou taxa" danger>
          A entrega só segue depois do pagamento. Pague no site da transportadora (nos Correios, em "Minhas Importações").
          {d.carrier?.url && (
            <a href={d.carrier.url} target="_blank" rel="noopener noreferrer" className="mt-1 flex items-center gap-1 font-medium underline underline-offset-2">
              Abrir {d.carrier.name} <ExternalLinkIcon className="size-3.5" aria-hidden />
            </a>
          )}
        </Alert>
      )}
      {stalled && !d.problem && (
        <Alert icon={ClockIcon} title={`Sem novidades há ${d.stalledDays} dias`}>
          Pode ser só a transportadora sem atualizar. Se continuar parado, fale com a loja.
        </Alert>
      )}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <Card>
          <CardHeader><CardTitle className="text-base">Histórico do rastreio</CardTitle></CardHeader>
          <CardContent>
            {!d.events?.length ? (
              <p className="text-sm text-muted-foreground">
                {d.tracking ? 'Ainda sem eventos. Pode levar algumas horas depois da postagem.' : 'Sem eventos.'}
              </p>
            ) : (
              <ol className="relative space-y-4 border-l pl-5">
                {d.events.map((e, i) => (
                  <li key={`${e.at}-${i}`} className="relative">
                    <span aria-hidden className={cn('absolute top-1 -left-[1.6rem] size-3 rounded-full border-2 border-card',
                      i === 0 ? (d.problem ? 'bg-destructive' : d.status === 'DELIVERED' ? 'bg-income' : 'bg-data') : 'bg-muted-foreground/40')} />
                    <p className={cn('text-sm', i === 0 && 'font-medium')}>{e.text}</p>
                    <p className="text-xs text-muted-foreground">
                      {e.at ? when(e.at) : ''}{e.location ? `${e.at ? ' · ' : ''}${e.location}` : ''}
                    </p>
                  </li>
                ))}
              </ol>
            )}
          </CardContent>
        </Card>
        <Card className="h-fit">
          <CardHeader><CardTitle className="text-base">Entrega</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            {d.address ? (
              <div>
                <p className="font-medium">{d.address.label}</p>
                <p className="text-muted-foreground">{addressLine(d.address)}</p>
                <p className="text-xs text-muted-foreground">CEP {d.address.cep.slice(0, 5)}-{d.address.cep.slice(5)}{d.address.recipient ? ` · ${d.address.recipient}` : ''}</p>
              </div>
            ) : d.addressShort ? (
              <p><span className="font-medium">{d.addressShort.label}</span> <span className="text-muted-foreground">({d.addressShort.city}/{d.addressShort.state})</span></p>
            ) : <p className="text-muted-foreground">Sem endereço.{d.mine && ' Escolha um em Editar.'}</p>}
            {d.group && <p className="text-muted-foreground">Compartilhada com {d.group.name}{!d.mine && d.owner ? ` por ${d.owner.name}` : ''}.</p>}
            <p className="text-xs text-muted-foreground">Cadastrada em {formatInstantDate(d.createdAt)}.</p>
          </CardContent>
        </Card>
      </div>

      {d.mine && (
        <>
          <DeliveryDialog open={editing} onOpenChange={setEditing} delivery={d} />
          <ConfirmDelete open={deleting} onOpenChange={setDeleting} title={`Excluir ${d.name}?`}
            description="O rastreio e a foto saem do LifePedia. Use Arquivar para guardar no histórico."
            onConfirm={() => void run(async () => { await remove.mutateAsync(d.id); navigate('/entregas') }, 'Entrega excluída.')} />
        </>
      )}
    </div>
  )
}

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-xl bg-muted/50 px-3 py-2">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="font-medium">{children}</dd>
    </div>
  )
}

function Alert({ icon: Icon, title, danger, children }: {
  icon: typeof ClockIcon
  title: string
  danger?: boolean
  children: ReactNode
}) {
  return (
    <div role="status" className={cn('flex gap-3 rounded-2xl border p-4 text-sm', danger ? 'border-destructive/40 bg-destructive/5' : 'bg-muted/40')}>
      <Icon className={cn('mt-0.5 size-5 shrink-0', danger ? 'text-destructive' : 'text-muted-foreground')} aria-hidden />
      <div className="space-y-0.5">
        <p className={cn('font-medium', danger && 'text-destructive')}>{title}</p>
        <div className="text-muted-foreground">{children}</div>
      </div>
    </div>
  )
}
