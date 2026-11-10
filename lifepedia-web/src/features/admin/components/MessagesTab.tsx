import { MegaphoneIcon, SendIcon } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { DataTablePagination } from '@/components/data-table/DataTablePagination'
import { useClampPage, useListState } from '@/components/data-table/list-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { formatInstantDate, formatRelative } from '@/lib/format'
import { useAdminAnnouncements, useBroadcast, useCreateAnnouncement, useEndAnnouncement } from '../api'
import { errorMessage } from '../labels'
import type { AnnouncementLevel, Audience } from '../types'
import { ConfirmAction, type PendingAction } from './ConfirmAction'

/** Duração do aviso em dias; 0 = até encerrar. */
const DURATIONS = [
  { value: '1', label: '1 dia' },
  { value: '3', label: '3 dias' },
  { value: '7', label: '7 dias' },
  { value: '30', label: '30 dias' },
  { value: '0', label: 'Até eu encerrar' },
]

/** Aviso no topo do app e notificação em massa. */
export function MessagesTab() {
  const [pending, setPending] = useState<PendingAction | null>(null)
  return (
    <div className="space-y-6">
      <div className="grid gap-6 xl:grid-cols-2">
        <AnnouncementForm onAsk={setPending} />
        <BroadcastForm onAsk={setPending} />
      </div>
      <AnnouncementList onAsk={setPending} />
      <ConfirmAction action={pending} onClose={() => setPending(null)} />
    </div>
  )
}

type AskProps = { onAsk: (action: PendingAction) => void }

function AnnouncementForm({ onAsk }: AskProps) {
  const [message, setMessage] = useState('')
  const [level, setLevel] = useState<AnnouncementLevel>('INFO')
  const [days, setDays] = useState('3')
  const create = useCreateAnnouncement()

  function submit(e: FormEvent) {
    e.preventDefault()
    const text = message.trim()
    if (!text) return
    onAsk({
      title: 'Publicar o aviso para todos?',
      description: `Aparece no topo do app para todas as pessoas ${days === '0' ? 'até você encerrar' : `por ${DURATIONS.find((d) => d.value === days)?.label}`}.`,
      confirmLabel: 'Publicar',
      run: () => create.mutate({
        message: text,
        level,
        endsAt: days === '0' ? null : new Date(Date.now() + Number(days) * 86_400_000).toISOString(),
      }, {
        onSuccess: () => {
          setMessage('')
          toast.success('Aviso publicado.')
        },
        onError: (err) => toast.error(errorMessage(err)),
      }),
    })
  }

  return (
    <Card>
      <CardHeader className="space-y-1">
        <CardTitle className="flex items-center gap-2"><MegaphoneIcon className="size-4" aria-hidden /> Aviso no topo do app</CardTitle>
        <CardDescription>Faixa para todas as pessoas logadas (manutenção, novidade importante). Até 3 ao mesmo tempo.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-3" onSubmit={submit}>
          <Field>
            <FieldLabel htmlFor="announcement-message">Mensagem</FieldLabel>
            <Textarea id="announcement-message" value={message} maxLength={500} rows={3} className="text-base md:text-sm"
              onChange={(e) => setMessage(e.target.value)} placeholder="Ex.: Manutenção hoje às 22h, o app pode ficar fora por alguns minutos." />
            <p className="text-right text-xs text-muted-foreground tabular-nums">{message.length}/500</p>
          </Field>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field>
              <FieldLabel>Tipo</FieldLabel>
              <Select value={level} onValueChange={(v) => setLevel(v as AnnouncementLevel)}>
                <SelectTrigger aria-label="Tipo do aviso"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="INFO">Informação</SelectItem>
                  <SelectItem value="WARNING">Atenção</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel>Duração</FieldLabel>
              <Select value={days} onValueChange={setDays}>
                <SelectTrigger aria-label="Duração do aviso"><SelectValue /></SelectTrigger>
                <SelectContent>{DURATIONS.map((d) => <SelectItem key={d.value} value={d.value}>{d.label}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
          </div>
          <Button type="submit" disabled={!message.trim() || create.isPending}><MegaphoneIcon /> Publicar aviso</Button>
        </form>
      </CardContent>
    </Card>
  )
}

function BroadcastForm({ onAsk }: AskProps) {
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [audience, setAudience] = useState<Audience>('ALL')
  const broadcast = useBroadcast()

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!title.trim() || !body.trim()) return
    onAsk({
      title: audience === 'ALL' ? 'Enviar para todas as pessoas?' : 'Enviar para os admins?',
      description: 'Vira uma notificação no app e, para quem ativou, no celular. Não dá para desfazer. Até 5 envios por dia.',
      confirmLabel: 'Enviar',
      run: () => broadcast.mutate({ title: title.trim(), body: body.trim(), audience }, {
        onSuccess: ({ recipients }) => {
          setTitle('')
          setBody('')
          toast.success(`Notificação enviada para ${recipients} pessoa(s).`)
        },
        onError: (err) => toast.error(errorMessage(err)),
      }),
    })
  }

  return (
    <Card>
      <CardHeader className="space-y-1">
        <CardTitle className="flex items-center gap-2"><SendIcon className="size-4" aria-hidden /> Notificação em massa</CardTitle>
        <CardDescription>Aparece nas notificações e no celular de quem ativou. Contas bloqueadas não recebem.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-3" onSubmit={submit}>
          <Field>
            <FieldLabel htmlFor="broadcast-title">Título</FieldLabel>
            <Input id="broadcast-title" value={title} maxLength={80} className="text-base md:text-sm" onChange={(e) => setTitle(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="broadcast-body">Mensagem</FieldLabel>
            <Textarea id="broadcast-body" value={body} maxLength={300} rows={3} className="text-base md:text-sm" onChange={(e) => setBody(e.target.value)} />
            <p className="text-right text-xs text-muted-foreground tabular-nums">{body.length}/300</p>
          </Field>
          <Field>
            <FieldLabel>Quem recebe</FieldLabel>
            <Select value={audience} onValueChange={(v) => setAudience(v as Audience)}>
              <SelectTrigger aria-label="Quem recebe"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Todas as pessoas</SelectItem>
                <SelectItem value="ADMINS">Só admins</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Button type="submit" disabled={!title.trim() || !body.trim() || broadcast.isPending}><SendIcon /> Enviar notificação</Button>
        </form>
      </CardContent>
    </Card>
  )
}

function AnnouncementList({ onAsk }: AskProps) {
  const list = useListState({ field: 'createdAt', direction: 'desc' })
  const { data, isLoading } = useAdminAnnouncements({ page: list.page, size: list.size })
  useClampPage(data, list.setPage)
  const end = useEndAnnouncement()

  return (
    <Card>
      <CardHeader><CardTitle>Avisos publicados</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        {isLoading ? <Skeleton className="h-24" /> : !data?.items.length ? (
          <p className="py-6 text-center text-sm text-muted-foreground">Nenhum aviso publicado ainda. Escreva um acima.</p>
        ) : (
          <ul className="divide-y">
            {data.items.map((a) => (
              <li key={a.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0 space-y-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <Badge variant={a.active ? 'secondary' : 'outline'}>{a.active ? 'No ar' : 'Encerrado'}</Badge>
                    <Badge variant="outline">{a.level === 'WARNING' ? 'Atenção' : 'Informação'}</Badge>
                  </div>
                  <p className="text-sm break-words whitespace-pre-line">{a.message}</p>
                  <p className="text-xs text-muted-foreground">
                    {a.createdByName ?? 'Conta excluída'} · {formatRelative(a.createdAt)}
                    {a.active && (a.endsAt ? ` · até ${formatInstantDate(a.endsAt)}` : ' · sem fim')}
                  </p>
                </div>
                {a.active && (
                  <Button variant="outline" size="sm" className="shrink-0" disabled={end.isPending} onClick={() => onAsk({
                    title: 'Encerrar o aviso?',
                    description: 'Ele some do topo do app para todos.',
                    confirmLabel: 'Encerrar',
                    destructive: true,
                    run: () => end.mutate(a.id, { onError: (err) => toast.error(errorMessage(err)) }),
                  })}>Encerrar</Button>
                )}
              </li>
            ))}
          </ul>
        )}
        <DataTablePagination data={data} list={list} />
      </CardContent>
    </Card>
  )
}
