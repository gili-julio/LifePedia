import { CheckCircle2Icon, ExternalLinkIcon, ListTodoIcon, MessageSquareIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { formatDate, formatRelative } from '@/lib/format'
import { cn } from '@/lib/utils'
import { itemNumber, PIPELINE } from '../labels'
import type { DevItem } from '../types'

type Props = {
  item: DevItem
  /** Mostra o projeto (listas que juntam vários) */
  showProject?: boolean
  provider?: string | null
}

/** MR, issue ou pipeline numa linha: título com link para o serviço, situação e selos. */
export function ItemRow({ item, showProject, provider }: Props) {
  const pipeline = item.pipeline ? PIPELINE[item.pipeline] : null
  const number = itemNumber(provider, item.kind, item.number)
  const title = item.kind === 'PIPELINE' ? `Pipeline de ${item.branch ?? 'branch principal'}` : item.title
  return (
    <li className="flex items-start gap-3 py-2.5">
      {pipeline ? (
        <span className={cn('mt-1.5 size-2.5 shrink-0 rounded-full', pipeline.dot)} title={`Pipeline: ${pipeline.label}`} aria-hidden />
      ) : <span className="mt-1.5 size-2.5 shrink-0 rounded-full bg-border" aria-hidden />}
      <div className="min-w-0 flex-1 space-y-1">
        <a href={item.webUrl} target="_blank" rel="noopener noreferrer"
          className="group flex items-start gap-1.5 text-sm font-medium underline-offset-2 hover:underline focus-visible:underline focus-visible:outline-none">
          <span className="min-w-0 break-words">
            {number && <span className="mr-1 text-muted-foreground tabular-nums">{number}</span>}{title}
          </span>
          <ExternalLinkIcon className="mt-0.5 size-3.5 shrink-0 opacity-50 group-hover:opacity-100" aria-label="abre em outra aba" />
        </a>
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
          {showProject && (
            <span className="flex items-center gap-1">
              <span className="size-2 rounded-full" style={{ background: item.projectColor }} aria-hidden />{item.projectName}
            </span>
          )}
          <span className="truncate">{item.repoName}</span>
          {pipeline && <span>Pipeline: {pipeline.label}</span>}
          {item.kind === 'MR' && item.state === 'merged' && <Badge variant="secondary">Aceito</Badge>}
          {item.draft && <Badge variant="outline">Rascunho</Badge>}
          {item.approved && (
            <span className="flex items-center gap-1 text-emerald-600 dark:text-emerald-400">
              <CheckCircle2Icon className="size-3.5" aria-hidden />Aprovado
            </span>
          )}
          {item.comments > 0 && (
            <span className="flex items-center gap-1"><MessageSquareIcon className="size-3.5" aria-hidden />{item.comments}</span>
          )}
          {item.review && item.author && <span>de {item.author}</span>}
          {item.dueDate && <span>Prazo {formatDate(item.dueDate)}</span>}
          {item.updatedAt && <span>{formatRelative(item.updatedAt)}</span>}
          {item.taskId && (
            <Link to={`/tarefas?tarefa=${item.taskId}`} className="flex items-center gap-1 underline-offset-2 hover:underline">
              <ListTodoIcon className="size-3.5" aria-hidden />Tarefa
            </Link>
          )}
        </div>
        {item.labels.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {item.labels.slice(0, 6).map((label) => <Badge key={label} variant="outline" className="font-normal">{label}</Badge>)}
          </div>
        )}
      </div>
    </li>
  )
}
