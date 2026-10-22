import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import {
  AlertCircleIcon,
  BellIcon,
  CheckIcon,
  ChevronDownIcon,
  ClockIcon,
  EllipsisVerticalIcon,
  FlameIcon,
  GripVerticalIcon,
  HashIcon,
  ListChecksIcon,
  type LucideIcon,
  MessageCircleIcon,
  RepeatIcon,
  StickyNoteIcon,
  UserRoundIcon,
  UsersIcon,
  WalletIcon,
} from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { useBoard } from '../board'
import { dayLabel, describeRepeat, formatTime, PRIORITY_COLOR, PRIORITY_LABEL, reminderLabel } from '../dates'
import type { LabelLookup } from '../labels'
import type { TaskItem } from '../types'

export type RowAction = { label: string; icon: LucideIcon; onSelect: () => void; destructive?: boolean }

type Props = {
  item: TaskItem
  labels: LabelLookup
  today: string
  /** Id único na lista arrastável (a mesma tarefa pode aparecer duas vezes no dia). */
  sortId: string
  /** Sem alça: ordem não é a manual ou a lista tem um item só. */
  dragDisabled: boolean
  /** Mostra o dia (atrasadas). */
  showDate?: boolean
  extra?: ReactNode
  busy?: boolean
  onToggle: () => void
  onOpen: () => void
  onCheck: (subtaskId: string, done: boolean) => void
  actions: RowAction[]
  /** Mobile: abre a folha de ações. */
  onMore: () => void
}

/** Tarefa na lista: círculo de concluir (cor da prioridade), título, detalhes, checklist e ações. */
export function TaskRow({ item, labels, today, sortId, dragDisabled, showDate, extra, busy, onToggle, onOpen, onCheck, actions, onMore }: Props) {
  const [open, setOpen] = useState(false)
  const board = useBoard()
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } =
    useSortable({ id: sortId, disabled: dragDisabled || busy })
  const done = item.status === 'DONE'
  const color = PRIORITY_COLOR[item.priority]
  const list = labels.list(item.listId)
  const tags = item.tagIds.map(labels.tag).filter((t) => !!t)
  const checked = item.subtasks.filter((s) => s.done).length
  const time = formatTime(item.dueTime, item.durationMinutes)
  // Visão pessoal: tarefa de grupo mostra o grupo; no grupo, o responsável (e quem concluiu, se foi outra pessoa).
  const foreign = item.groupId !== null && item.groupId !== board.groupId
  const assignee = board.groupId ? board.personName(item.assigneeId) : null
  const doneBy = done && item.completedBy !== item.assigneeId ? board.personName(item.completedBy) : null

  return (
    <li ref={setNodeRef} style={{ transform: CSS.Translate.toString(transform), transition }}
      className={cn('relative flex gap-2 bg-card py-3', isDragging && 'z-10 rounded-lg shadow-lg ring-1 ring-border')}>
      {!dragDisabled && (
        <button ref={setActivatorNodeRef} type="button" {...attributes} {...listeners} disabled={busy}
          aria-label={`Arrastar ${item.title} para mudar a ordem`}
          className="-my-3 -ml-2 flex w-5 shrink-0 cursor-grab touch-none items-center justify-center rounded-md text-muted-foreground outline-none hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 active:cursor-grabbing disabled:pointer-events-none disabled:opacity-40">
          <GripVerticalIcon className="size-4" />
        </button>
      )}
      <button type="button" role="checkbox" aria-checked={done} disabled={busy} onClick={onToggle}
        aria-label={done ? `Desmarcar ${item.title}` : `Concluir ${item.title}`}
        title={item.priority !== 'NONE' ? `Prioridade ${PRIORITY_LABEL[item.priority].toLowerCase()}` : undefined}
        className={cn(
          'relative mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full border-2 outline-none transition-colors motion-reduce:transition-none after:absolute after:-inset-2',
          'focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50',
          done ? 'border-transparent bg-income text-background' : 'border-muted-foreground/60 hover:bg-muted',
        )}
        style={!done && color ? { borderColor: color, background: `${color}1a` } : undefined}>
        {done && <CheckIcon className="size-3.5" strokeWidth={3} />}
      </button>

      <div className="min-w-0 flex-1 space-y-1">
        <button type="button" onClick={onOpen}
          className={cn('block w-full text-left leading-snug outline-none focus-visible:underline',
            done && 'text-muted-foreground line-through')}>
          {item.title}
        </button>
        <div className="flex flex-wrap items-center gap-x-2.5 gap-y-1 text-xs text-muted-foreground">
          {showDate && item.scheduledDate && (
            <span className="flex items-center gap-1">
              {item.overdue && <AlertCircleIcon className="size-3.5 text-destructive" aria-hidden />}
              {dayLabel(item.scheduledDate, today)}
            </span>
          )}
          {time && <span className="flex items-center gap-1"><ClockIcon className="size-3.5" aria-hidden />{time}</span>}
          {item.reminderMinutes !== null && item.dueTime && (
            <BellIcon className="size-3.5" aria-label={`Lembrete: ${reminderLabel(item.reminderMinutes).toLowerCase()}`} />
          )}
          {item.habit && item.streak !== null && item.streak > 0 && (
            <span className="flex items-center gap-0.5 font-medium text-foreground"
              title={`Sequência de ${item.streak}`} aria-label={`Sequência de ${item.streak}`}>
              <FlameIcon className="size-3.5 text-orange-500" aria-hidden />
              <span className="tabular-nums" aria-hidden>{item.streak}</span>
            </span>
          )}
          {item.repeat && (
            <span className="flex items-center gap-1" title={describeRepeat(item.repeat, item.date)}>
              <RepeatIcon className="size-3.5" aria-hidden />
              <span className="sr-only">{describeRepeat(item.repeat, item.date)}</span>
            </span>
          )}
          {foreign && (
            <span className="flex min-w-0 items-center gap-1 font-medium text-foreground">
              <UsersIcon className="size-3.5 shrink-0" aria-hidden />
              <span className="truncate">{board.groupName(item.groupId) ?? 'Grupo'}</span>
            </span>
          )}
          {assignee && (
            <span className="flex items-center gap-1" title={item.rotating ? 'Vez no rodízio' : 'Responsável'}>
              <UserRoundIcon className="size-3.5" aria-hidden />
              <span className="sr-only">{item.rotating ? 'Vez de' : 'Responsável:'}</span>{assignee}
            </span>
          )}
          {doneBy && <span>feita por {doneBy === 'Você' ? 'você' : doneBy}</span>}
          {item.notes && <StickyNoteIcon className="size-3.5" aria-label="Tem notas" />}
          {item.recurringEntryId && <WalletIcon className="size-3.5" aria-label="Ligada a um fixo do financeiro" />}
          {item.commentCount > 0 && (
            <span className="flex items-center gap-1" aria-label={`${item.commentCount} comentário${item.commentCount === 1 ? '' : 's'}`}>
              <MessageCircleIcon className="size-3.5" aria-hidden />
              <span className="tabular-nums" aria-hidden>{item.commentCount}</span>
            </span>
          )}
          {item.subtasks.length > 0 && (
            <button type="button" onClick={() => setOpen((v) => !v)} aria-expanded={open}
              aria-label={`Subtarefas: ${checked} de ${item.subtasks.length} feitas`}
              className="relative flex items-center gap-1 rounded outline-none after:absolute after:-inset-2 hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50">
              <ListChecksIcon className="size-3.5" aria-hidden />
              <span className="tabular-nums">{checked}/{item.subtasks.length}</span>
              <ChevronDownIcon className={cn('size-3 transition-transform motion-reduce:transition-none', open && 'rotate-180')} aria-hidden />
            </button>
          )}
          {list && (
            <span className="flex min-w-0 items-center gap-1">
              <span className="size-2 shrink-0 rounded-full" style={{ background: list.color }} aria-hidden />
              <span className="truncate">{list.name}</span>
            </span>
          )}
          {tags.map((tag) => (
            <span key={tag.id} className="flex items-center gap-0.5">
              <HashIcon className="size-3" style={{ color: tag.color }} aria-hidden />{tag.name}
            </span>
          ))}
          {extra}
        </div>
        {open && (
          <ul className="space-y-1.5 pt-1">
            {item.subtasks.map((subtask) => (
              <li key={subtask.id} className="flex items-center gap-2 text-sm">
                <Checkbox id={`${sortId}-${subtask.id}`} checked={subtask.done} disabled={busy}
                  onCheckedChange={(value) => onCheck(subtask.id, value === true)} />
                <label htmlFor={`${sortId}-${subtask.id}`} className={cn(subtask.done && 'text-muted-foreground line-through')}>
                  {subtask.title}
                </label>
              </li>
            ))}
          </ul>
        )}
      </div>

      {actions.length > 0 && (
        <>
          <Button variant="ghost" size="icon-sm" className="-mr-2 md:hidden" aria-label={`Opções de ${item.title}`} onClick={onMore}>
            <EllipsisVerticalIcon />
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" className="-mr-2 hidden md:inline-flex" aria-label={`Opções de ${item.title}`}>
                <EllipsisVerticalIcon />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {actions.map(({ label, icon: Icon, onSelect, destructive }) => (
                <DropdownMenuItem key={label} onSelect={onSelect} variant={destructive ? 'destructive' : 'default'}>
                  <Icon /> {label}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </>
      )}
    </li>
  )
}
