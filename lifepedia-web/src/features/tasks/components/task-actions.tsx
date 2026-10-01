import { CalendarClockIcon, CheckCheckIcon, PencilIcon, SkipForwardIcon, Trash2Icon, UserRoundIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ActionSheet } from '@/components/ActionSheet'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { ApiError } from '@/lib/api'
import { currentYearMonth } from '@/lib/format'
import {
  useAssignOccurrence,
  useCheckSubtask,
  useDeleteTask,
  useMoveTask,
  useReorderTasks,
  useResolveOverdue,
  useSetStatus,
} from '../api'
import { dayLabel } from '../dates'
import { occurrenceKey } from '../labels'
import type { OccurrenceStatus, Scope, TaskItem } from '../types'
import { AssignDialog } from './AssignDialog'
import { type FixedPayment, FixedPaymentDialog } from './FixedPaymentDialog'
import { MoveDialog } from './MoveDialog'
import { ScopeDialog, type ScopeOption } from './ScopeDialog'
import type { TaskTarget } from './TaskDialog'
import type { RowAction } from './TaskRow'

type Dialog = { kind: 'move' | 'assign' | 'delete' | 'sheet'; item: TaskItem; open: boolean; group?: number }

function fail(error: unknown, fallback: string) {
  toast.error(error instanceof ApiError ? error.message : fallback)
}

/**
 * Ações das linhas (concluir, subtarefa, adiar, pular, responsável, excluir, reordenar) e os dialogs que elas abrem.
 * Cada ação vai para as rotas do dono da tarefa (`item.groupId`), inclusive as de grupo na visão pessoal.
 */
export function useTaskActions({ today, groupId, onEdit }: {
  today: string
  /** Quadro atual (nulo = pessoal): reordenar vale só para as tarefas dele. */
  groupId: string | null
  onEdit: (target: TaskTarget) => void
}) {
  const setStatus = useSetStatus()
  const check = useCheckSubtask()
  const move = useMoveTask()
  const remove = useDeleteTask()
  const overdue = useResolveOverdue()
  const reorder = useReorderTasks()
  const assign = useAssignOccurrence()
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [payment, setPayment] = useState<{ value: FixedPayment; open: boolean } | null>(null)

  async function withBusy(item: TaskItem, action: () => Promise<unknown>, fallback: string) {
    setBusyKey(occurrenceKey(item))
    try {
      await action()
      return true
    } catch (error) {
      fail(error, fallback)
      return false
    } finally {
      setBusyKey(null)
    }
  }

  function status(item: TaskItem, next: OccurrenceStatus) {
    return setStatus.mutateAsync({ groupId: item.groupId, id: item.taskId, date: item.date, status: next })
  }

  async function toggle(item: TaskItem) {
    const done = item.status === 'DONE'
    const ok = await withBusy(item, () => status(item, done ? 'PENDING' : 'DONE'), 'Não foi possível atualizar.')
    if (ok && !done) {
      toast.success('Tarefa concluída.', {
        action: { label: 'Desfazer', onClick: () => void status(item, 'PENDING').catch((e) => fail(e, 'Não foi possível desfazer.')) },
      })
      // Ligada a um fixo: oferece registrar o valor do mês da ocorrência.
      if (item.recurringEntryId && !item.groupId) {
        const month = item.date?.slice(0, 7) ?? currentYearMonth()
        setPayment({ value: { entryId: item.recurringEntryId, month, title: item.title }, open: true })
      }
    }
  }

  async function skip(item: TaskItem) {
    const ok = await withBusy(item, () => status(item, 'SKIPPED'), 'Não foi possível pular.')
    if (ok) {
      toast.success('Ocorrência pulada.', {
        action: { label: 'Desfazer', onClick: () => void status(item, 'PENDING').catch((e) => fail(e, 'Não foi possível desfazer.')) },
      })
    }
  }

  async function resolveOverdue(item: TaskItem, next: OccurrenceStatus) {
    try {
      const { updated } = await overdue.mutateAsync({ groupId: item.groupId, id: item.taskId, status: next })
      toast.success(`${updated} ${updated === 1 ? 'ocorrência' : 'ocorrências'} ${next === 'DONE' ? 'concluída' : 'pulada'}${updated === 1 ? '' : 's'}.`)
    } catch (error) {
      fail(error, 'Não foi possível atualizar.')
    }
  }

  async function moveTo(item: TaskItem, to: string | null) {
    const ok = await withBusy(item, () => move.mutateAsync({ groupId: item.groupId, id: item.taskId, date: item.date, to }), 'Não foi possível mover.')
    if (ok) toast.success(to ? `Movida para ${dayLabel(to, today).toLowerCase()}.` : 'Tarefa ficou sem data.')
    return ok
  }

  async function deleteWith(item: TaskItem, scope: Scope) {
    const ok = await withBusy(item, () => remove.mutateAsync({ groupId: item.groupId, id: item.taskId, scope, date: item.date }), 'Não foi possível excluir.')
    if (ok) toast.success(scope === 'THIS' ? 'Ocorrência removida.' : 'Tarefa excluída.')
  }

  async function assignTo(item: TaskItem, assigneeId: string | null) {
    const ok = await withBusy(item, () => assign.mutateAsync({ groupId: item.groupId, id: item.taskId, date: item.date, assigneeId }),
      'Não foi possível mudar o responsável.')
    if (ok) toast.success('Responsável alterado.')
  }

  /** Itens de menu da linha; `group` = quantas atrasadas da mesma tarefa estão juntas. */
  function rowActions(item: TaskItem, group = 1): RowAction[] {
    const pending = item.status === 'PENDING'
    const actions: RowAction[] = [
      { label: 'Editar', icon: PencilIcon, onSelect: () => onEdit({ mode: 'edit', groupId: item.groupId, taskId: item.taskId, occurrence: item }) },
    ]
    if (pending && item.groupId) {
      actions.push({ label: 'Mudar responsável…', icon: UserRoundIcon, onSelect: () => setDialog({ kind: 'assign', item, open: true }) })
    }
    if (pending) actions.push({ label: 'Mover para…', icon: CalendarClockIcon, onSelect: () => setDialog({ kind: 'move', item, open: true }) })
    if (pending && item.repeat) actions.push({ label: 'Pular esta', icon: SkipForwardIcon, onSelect: () => void skip(item) })
    if (group > 1) {
      actions.push(
        { label: `Concluir as ${group} atrasadas`, icon: CheckCheckIcon, onSelect: () => void resolveOverdue(item, 'DONE') },
        { label: `Pular as ${group} atrasadas`, icon: SkipForwardIcon, onSelect: () => void resolveOverdue(item, 'SKIPPED') },
      )
    }
    if (item.canDelete) {
      actions.push({ label: 'Excluir', icon: Trash2Icon, destructive: true, onSelect: () => setDialog({ kind: 'delete', item, open: true }) })
    }
    return actions
  }

  const current = dialog?.item ?? null
  const close = (open: boolean) => setDialog((d) => (d ? { ...d, open } : d))
  const fixedRule = !!current?.repeat && !current.repeat.fromCompletion
  const deleteOptions: ScopeOption[] = fixedRule
    ? [
        { scope: 'THIS', label: 'Só esta', hint: 'As outras ocorrências continuam.' },
        { scope: 'FOLLOWING', label: 'Esta e as próximas', hint: 'As anteriores continuam no histórico.' },
        { scope: 'ALL', label: 'Todas', hint: 'Apaga a tarefa e o histórico dela.' },
      ]
    : [
        { scope: 'THIS', label: 'Só esta', hint: 'A próxima continua contando a partir de hoje.' },
        { scope: 'ALL', label: 'A tarefa inteira', hint: 'Apaga a tarefa e o histórico dela.' },
      ]

  const dialogs = (
    <>
      <MoveDialog item={dialog?.kind === 'move' ? current : null} today={today}
        open={dialog?.kind === 'move' && dialog.open} onOpenChange={close}
        onMove={(to) => current && void moveTo(current, to)} />
      <AssignDialog item={dialog?.kind === 'assign' ? current : null} today={today}
        open={dialog?.kind === 'assign' && dialog.open} onOpenChange={close}
        onChoose={(id) => current && void assignTo(current, id)} />
      {current?.repeat ? (
        <ScopeDialog open={dialog?.kind === 'delete' && dialog.open} onOpenChange={close} destructive
          title="Excluir tarefa repetida" description={current.title} options={deleteOptions}
          onChoose={(scope) => void deleteWith(current, scope)} />
      ) : (
        <ConfirmDelete open={dialog?.kind === 'delete' && dialog.open} onOpenChange={close}
          title="Excluir tarefa?" description={current ? `“${current.title}” será excluída.` : ''}
          onConfirm={() => current && void deleteWith(current, 'ALL')} />
      )}
      <FixedPaymentDialog payment={payment?.value ?? null} open={!!payment?.open}
        onOpenChange={(open) => setPayment((p) => (p ? { ...p, open } : p))} />
      {current && (
        <ActionSheet open={dialog?.kind === 'sheet' && dialog.open} onOpenChange={close} title={current.title}
          actions={rowActions(current, dialog?.group)} />
      )}
    </>
  )

  return {
    busyKey,
    toggle,
    checkSubtask: (item: TaskItem, subtaskId: string, done: boolean) =>
      void check.mutateAsync({ groupId: item.groupId, id: item.taskId, subtaskId, date: item.date, done })
        .catch((e) => fail(e, 'Não foi possível marcar.')),
    rowActions,
    /** Muda o dia só desta ocorrência (arrastar na semana). */
    move: (item: TaskItem, to: string) => moveTo(item, to),
    openSheet: (item: TaskItem, group?: number) => setDialog({ kind: 'sheet', item, open: true, group }),
    /** Só as tarefas do quadro atual (na visão pessoal, as de grupo não se movem). */
    reorder: (taskIds: string[]) => reorder.mutateAsync({ groupId, taskIds }),
    dialogs,
  }
}

export type TaskActions = ReturnType<typeof useTaskActions>
