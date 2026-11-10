import { DataTablePagination } from '@/components/data-table/DataTablePagination'
import { useClampPage, useListState } from '@/components/data-table/list-state'
import { Skeleton } from '@/components/ui/skeleton'
import { formatInstantDate, formatTime } from '@/lib/format'
import { useAuditLog } from '../api'
import { AUDIT_LABEL } from '../labels'

/** Registro das ações dos admins, da mais nova para a mais antiga. */
export function AuditTab() {
  const list = useListState({ field: 'createdAt', direction: 'desc' })
  const { data, isLoading } = useAuditLog({ page: list.page, size: list.size })
  useClampPage(data, list.setPage)

  return (
    <div className="space-y-3">
      {isLoading ? (
        <div className="space-y-2">{Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-12" />)}</div>
      ) : !data?.items.length ? (
        <p className="py-12 text-center text-sm text-muted-foreground">Nenhuma ação registrada ainda.</p>
      ) : (
        <ul className="divide-y">
          {data.items.map((entry) => (
            <li key={entry.id} className="flex flex-col gap-0.5 py-3 sm:flex-row sm:items-start sm:gap-4">
              <time dateTime={entry.createdAt} className="shrink-0 text-xs text-muted-foreground tabular-nums sm:w-32 sm:pt-0.5">
                {formatInstantDate(entry.createdAt)} {formatTime(entry.createdAt)}
              </time>
              <div className="min-w-0 text-sm">
                <p>
                  <span className="font-medium">{entry.actorName}</span>{' '}
                  <span className="text-muted-foreground">{AUDIT_LABEL[entry.action]?.toLowerCase() ?? entry.action}</span>
                  {entry.targetName && <> · <span className="font-medium">{entry.targetName}</span></>}
                </p>
                {entry.detail && <p className="break-words text-muted-foreground">{entry.detail}</p>}
              </div>
            </li>
          ))}
        </ul>
      )}
      <DataTablePagination data={data} list={list} />
    </div>
  )
}
