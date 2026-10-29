import { CalendarCheck2Icon, EllipsisVerticalIcon, LinkIcon, PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ActionSheet } from '@/components/ActionSheet'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { scheduleLabel } from '../absences'
import { useCommitments, useDeleteCommitment } from '../api'
import { AbsenceMeter } from '../components/AbsenceMeter'
import { CommitmentDialog } from '../components/CommitmentDialog'
import { DaysDialog } from '../components/DaysDialog'
import type { Commitment } from '../types'

/** Frequência: compromissos (ex.: matérias) com dias, horário e limite de faltas. */
export function AttendancePage() {
  const { data, isLoading, isError } = useCommitments()
  const remove = useDeleteCommitment()
  const [editing, setEditing] = useState<{ open: boolean; item?: Commitment }>({ open: false })
  const [days, setDays] = useState<Commitment | null>(null)
  const [menu, setMenu] = useState<{ open: boolean; item?: Commitment }>({ open: false })
  const [deleting, setDeleting] = useState<{ open: boolean; item?: Commitment }>({ open: false })

  async function onDelete(item: Commitment) {
    try {
      await remove.mutateAsync(item.id)
      toast.success('Compromisso excluído.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir.')
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Frequência" description="Seus compromissos com presença, faltas e o limite de cada um."
        actions={[{ label: 'Novo compromisso', icon: PlusIcon, primary: true, onSelect: () => setEditing({ open: true }) }]} />

      {isError ? <p className="text-sm text-destructive">Não foi possível carregar a frequência.</p>
        : isLoading || !data ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-52 w-full rounded-xl" />)}
          </div>
        ) : data.length === 0 ? (
          <Card>
            <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
              <CalendarCheck2Icon className="size-8 text-muted-foreground" aria-hidden />
              <div className="space-y-1">
                <p className="font-medium">Nenhum compromisso ainda</p>
                <p className="text-sm text-muted-foreground">
                  Cadastre uma matéria ou outro compromisso fixo para acompanhar presenças e faltas.
                </p>
              </div>
              <Button onClick={() => setEditing({ open: true })}><PlusIcon /> Novo compromisso</Button>
            </CardContent>
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {data.map((c) => (
              <CommitmentCard key={c.id} commitment={c} onDays={() => setDays(c)} onMenu={() => setMenu({ open: true, item: c })} />
            ))}
          </div>
        )}

      <CommitmentDialog open={editing.open} commitment={editing.item}
        onOpenChange={(open) => setEditing((s) => ({ ...s, open }))} />
      <DaysDialog commitment={days} onOpenChange={(open) => !open && setDays(null)} />
      <ActionSheet open={menu.open} onOpenChange={(open) => setMenu((s) => ({ ...s, open }))} title={menu.item?.name ?? ''}
        actions={[
          { label: 'Editar', icon: PencilIcon, onSelect: () => setEditing({ open: true, item: menu.item }) },
          { label: 'Excluir', icon: Trash2Icon, destructive: true, onSelect: () => setDeleting({ open: true, item: menu.item }) },
        ]} />
      <ConfirmDelete open={deleting.open} onOpenChange={(open) => setDeleting((s) => ({ ...s, open }))}
        title="Excluir compromisso?" description="As presenças e faltas registradas também serão apagadas. A tarefa ligada continua."
        onConfirm={() => deleting.item && void onDelete(deleting.item)} />
    </div>
  )
}

function CommitmentCard({ commitment: c, onDays, onMenu }: { commitment: Commitment; onDays: () => void; onMenu: () => void }) {
  const { stats } = c
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex min-w-0 items-center gap-2">
          <span className="size-3 shrink-0 rounded-full" style={{ background: c.color }} aria-hidden />
          <span className="truncate">{c.name}</span>
        </CardTitle>
        <CardDescription className="tabular-nums">
          {scheduleLabel(c.weekdays, c.time, c.durationMinutes)}
          <br />
          {c.endDate ? `${formatDate(c.startDate)} a ${formatDate(c.endDate)}` : `Desde ${formatDate(c.startDate)}`}
        </CardDescription>
        <CardAction>
          <Button variant="ghost" size="icon-sm" aria-label={`Opções de ${c.name}`} onClick={onMenu}>
            <EllipsisVerticalIcon />
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        <AbsenceMeter absences={stats.absences} max={c.maxAbsences} />
        <p className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground tabular-nums">
          <span>{stats.presences} {stats.presences === 1 ? 'presença' : 'presenças'}</span>
          {stats.canceled > 0 && <span>{stats.canceled} sem aula</span>}
          {stats.pending > 0 && (
            <span className="font-medium text-orange-700 dark:text-orange-300">
              {stats.pending} {stats.pending === 1 ? 'pendente' : 'pendentes'}
            </span>
          )}
        </p>
        {c.task && (
          <p className="flex min-w-0 items-center gap-1.5 text-xs text-muted-foreground">
            <LinkIcon className="size-3.5 shrink-0" aria-hidden />
            <span className="truncate">Tarefa: {c.task.title}</span>
          </p>
        )}
        <Button variant={stats.pending > 0 ? 'default' : 'outline'} size="sm" className="w-full" onClick={onDays}>
          {stats.pending > 0 ? 'Marcar pendentes' : 'Ver dias'}
        </Button>
      </CardContent>
    </Card>
  )
}
