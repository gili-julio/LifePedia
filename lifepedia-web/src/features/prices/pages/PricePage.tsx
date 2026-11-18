import {
  ArrowLeftIcon,
  ExternalLinkIcon,
  LinkIcon,
  PencilIcon,
  PencilLineIcon,
  PlusIcon,
  RefreshCwIcon,
  ShoppingBagIcon,
  TargetIcon,
  Trash2Icon,
  XIcon,
} from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate, formatRelative } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useAddWatchLink, useDeleteWatch, usePriceWatch, useRefreshWatch, useRemoveWatchLink, useRemoveWatchPrice } from '../api'
import { ChangeBadge } from '../components/ChangeBadge'
import { ManualPriceDialog } from '../components/ManualPriceDialog'
import { OffersTable } from '../components/OffersTable'
import { PriceChart } from '../components/PriceChart'
import { WatchDialog } from '../components/WatchDialog'

/** Detalhe do item: menor preço, variação, mínimo/média/máximo, histórico, lojas, links e preços anotados. */
export function PricePage() {
  const { watchId = '' } = useParams()
  const navigate = useNavigate()
  const { formatCurrency } = useMoney()
  const { data, isError, error } = usePriceWatch(watchId)
  const refresh = useRefreshWatch()
  const remove = useDeleteWatch()
  const addLink = useAddWatchLink()
  const removeLink = useRemoveWatchLink()
  const removePrice = useRemoveWatchPrice()
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [noting, setNoting] = useState(false)
  const [link, setLink] = useState('')

  if (isError) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-destructive">{error instanceof ApiError ? error.message : 'Não foi possível abrir o item.'}</p>
        <Button variant="outline" asChild><Link to="/precos"><ArrowLeftIcon /> Voltar para Preços</Link></Button>
      </div>
    )
  }
  if (!data) return <Skeleton className="h-96" />

  const { watch: w, stats } = data

  async function run(action: () => Promise<unknown>, done: string) {
    try {
      await action()
      toast.success(done)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível concluir.')
    }
  }

  async function submitLink() {
    if (!link.trim()) return
    await run(() => addLink.mutateAsync({ id: w.id, url: link.trim() }), 'Link adicionado.')
    setLink('')
  }

  const actions = [
    { label: 'Atualizar agora', icon: RefreshCwIcon, primary: true, onSelect: () => void run(() => refresh.mutateAsync(w.id), 'Preços atualizados.') },
    { label: 'Anotar preço', icon: PencilLineIcon, onSelect: () => setNoting(true) },
    { label: 'Editar', icon: PencilIcon, onSelect: () => setEditing(true) },
    ...(w.canDelete ? [{ label: 'Excluir', icon: Trash2Icon, secondary: true, destructive: true, onSelect: () => setDeleting(true) }] : []),
  ]

  return (
    <div className="space-y-6">
      <Link to="/precos" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeftIcon className="size-4" aria-hidden /> Preços
      </Link>
      <PageHeader title={w.name} description={w.groupName ? `Grupo ${w.groupName}` : 'Minha lista de desejos'} actions={actions} hideValues />

      <Card>
        <CardContent className="flex flex-col gap-4 sm:flex-row sm:items-center">
          <span className="flex size-24 shrink-0 items-center justify-center self-center overflow-hidden rounded-xl bg-muted">
            {w.imageUrl ? <img src={w.imageUrl} alt="" className="size-full object-contain" />
              : <ShoppingBagIcon className="size-8 text-muted-foreground" aria-hidden />}
          </span>
          <div className="min-w-0 flex-1 space-y-1">
            <p className="text-sm text-muted-foreground">Menor preço agora</p>
            {w.bestPrice !== null ? (
              <>
                <p className="flex flex-wrap items-center gap-2">
                  <span className="text-3xl font-semibold tabular-nums">{formatCurrency(w.bestPrice)}</span>
                  <ChangeBadge value={w.change7} period="em 7 dias" />
                  <ChangeBadge value={w.change30} period="em 30 dias" />
                </p>
                <p className="text-sm">
                  em {w.bestUrl ? (
                    <a href={w.bestUrl} target="_blank" rel="noopener noreferrer nofollow" className="inline-flex items-center gap-1 font-medium underline-offset-2 hover:underline">
                      {w.bestStore} <ExternalLinkIcon className="size-3.5" aria-hidden />
                    </a>
                  ) : <span className="font-medium">{w.bestStore}</span>}
                  {w.storeCount ? <span className="text-muted-foreground"> · {w.storeCount} {w.storeCount === 1 ? 'loja' : 'lojas'}</span> : null}
                </p>
              </>
            ) : <p className="text-sm">{w.lastError ?? 'Ainda sem preços.'}</p>}
            {w.targetPrice !== null && (
              <p className={w.atTarget ? 'flex items-center gap-1 text-sm font-medium text-income' : 'flex items-center gap-1 text-sm text-muted-foreground'}>
                <TargetIcon className="size-4" aria-hidden /> {w.atTarget ? 'Chegou no alvo de' : 'Alvo:'} {formatCurrency(w.targetPrice)}
              </p>
            )}
            <p className="text-xs text-muted-foreground">
              {w.lastCheckedAt ? `Consultado ${formatRelative(w.lastCheckedAt)}` : 'Ainda não consultado'}
              {w.active ? ' · confere todo dia' : ' · parado'}
              {w.lastError && w.bestPrice !== null ? ` · ${w.lastError}` : ''}
            </p>
          </div>
        </CardContent>
      </Card>

      <div className="grid grid-cols-3 gap-3">
        <Stat label="Mínimo (90 dias)" value={stats.min} />
        <Stat label="Média" value={stats.avg} />
        <Stat label="Máximo" value={stats.max} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Histórico do menor preço</CardTitle>
          <CardDescription>Um ponto por dia, de qualquer loja. O Buscapé traz os últimos dias já na primeira consulta.</CardDescription>
        </CardHeader>
        <CardContent>
          {data.history.length ? <PriceChart history={data.history} target={w.targetPrice} />
            : <p className="text-sm text-muted-foreground">Sem histórico ainda.</p>}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Lojas</CardTitle>
          <CardDescription>
            {data.buscapeUrl ? <>Do <a href={data.buscapeUrl} target="_blank" rel="noopener noreferrer nofollow" className="underline underline-offset-2">Buscapé</a>, dos links e dos preços anotados.</>
              : 'Dos links e dos preços anotados (o produto não foi achado no Buscapé).'}
          </CardDescription>
        </CardHeader>
        <CardContent><OffersTable offers={data.offers} /></CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader><CardTitle className="text-base">Links das lojas</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            {data.links.length ? (
              <ul className="space-y-2">
                {data.links.map((l) => (
                  <Row key={l.id} title={l.store ?? new URL(l.url).hostname}
                    detail={l.lastError ?? (l.lastPrice !== null ? formatCurrency(l.lastPrice) : 'Ainda não lido')}
                    href={l.url} onRemove={() => void run(() => removeLink.mutateAsync({ id: w.id, linkId: l.id }), 'Link removido.')} />
                ))}
              </ul>
            ) : <p className="text-sm text-muted-foreground">Nenhum link. Cole o endereço do produto numa loja.</p>}
            <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); void submitLink() }}>
              <Input type="url" value={link} maxLength={2048} placeholder="https://loja.com.br/produto" aria-label="Link da loja"
                onChange={(e) => setLink(e.target.value)} />
              <Button type="submit" variant="outline" disabled={!link.trim() || addLink.isPending}><LinkIcon /> Adicionar</Button>
            </form>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Preços anotados</CardTitle>
            <CardAction><Button size="sm" variant="outline" onClick={() => setNoting(true)}><PlusIcon /> Anotar</Button></CardAction>
          </CardHeader>
          <CardContent>
            {data.manual.length ? (
              <ul className="space-y-2">
                {data.manual.map((m) => (
                  <Row key={m.id} title={`${m.store} · ${formatCurrency(m.price)}`} detail={formatDate(m.day)} href={m.url}
                    onRemove={() => void run(() => removePrice.mutateAsync({ id: w.id, pointId: m.id }), 'Preço removido.')} />
                ))}
              </ul>
            ) : <p className="text-sm text-muted-foreground">Viu um preço na loja ou no app? Anote aqui.</p>}
          </CardContent>
        </Card>
      </div>

      <WatchDialog open={editing} onOpenChange={setEditing} watch={w} />
      <ManualPriceDialog watchId={w.id} open={noting} onOpenChange={setNoting} />
      <ConfirmDelete open={deleting} onOpenChange={setDeleting} title={`Excluir ${w.name}?`}
        description="O histórico, os links e os preços anotados saem junto."
        onConfirm={() => void run(async () => { await remove.mutateAsync(w.id); navigate('/precos') }, 'Item excluído.')} />
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number | null }) {
  const { formatCurrency } = useMoney()
  return (
    <div className="rounded-xl bg-muted/50 px-3 py-2">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-semibold tabular-nums">{value === null ? '-' : formatCurrency(value)}</p>
    </div>
  )
}

function Row({ title, detail, href, onRemove }: { title: ReactNode; detail: string; href: string | null; onRemove: () => void }) {
  return (
    <li className="flex items-center gap-2 rounded-lg border px-3 py-2">
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{title}</p>
        <p className="truncate text-xs text-muted-foreground">{detail}</p>
      </div>
      {href && (
        <a href={href} target="_blank" rel="noopener noreferrer nofollow" aria-label="Abrir link"
          className="inline-flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground">
          <ExternalLinkIcon className="size-4" />
        </a>
      )}
      <Button size="icon-sm" variant="ghost" aria-label="Remover" onClick={onRemove}><XIcon /></Button>
    </li>
  )
}
