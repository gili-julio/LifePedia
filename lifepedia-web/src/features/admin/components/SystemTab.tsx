import { CheckCircle2Icon, CircleDashedIcon, CircleXIcon, Loader2Icon, RefreshCwIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatInstantDate, formatRelative, formatTime } from '@/lib/format'
import { useSystemStatus } from '../api'
import { formatBytes, formatDuration, JOB_LABEL } from '../labels'
import type { JobStatus } from '../types'

const JOB_ICON: Record<JobStatus, ReactNode> = {
  NONE: <CircleDashedIcon className="size-4 text-muted-foreground" aria-hidden />,
  STARTED: <Loader2Icon className="size-4 animate-spin text-data motion-reduce:animate-none" aria-hidden />,
  SUCCESS: <CheckCircle2Icon className="size-4 text-income" aria-hidden />,
  ERROR: <CircleXIcon className="size-4 text-destructive" aria-hidden />,
}

const when = (iso: string) => `${formatInstantDate(iso)} ${formatTime(iso)}`

/** Situação do servidor: versão, memória, banco, recursos ligados e tarefas agendadas. */
export function SystemTab() {
  const { data, isFetching, refetch } = useSystemStatus()
  if (!data) return <div className="grid gap-4 xl:grid-cols-3">{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-40" />)}</div>

  return (
    <div className="space-y-6">
      <div className="flex justify-end">
        <Button variant="outline" size="sm" disabled={isFetching} onClick={() => refetch()}>
          <RefreshCwIcon className={isFetching ? 'animate-spin motion-reduce:animate-none' : undefined} /> Atualizar
        </Button>
      </div>
      <div className="grid gap-4 xl:grid-cols-3">
        <Card size="sm">
          <CardHeader><CardTitle>API</CardTitle></CardHeader>
          <CardContent>
            <dl className="space-y-2 text-sm">
              <Row label="Versão">{data.version ?? 'Desenvolvimento'}</Row>
              {data.buildTime && <Row label="Build">{when(data.buildTime)}</Row>}
              <Row label="No ar há">{formatDuration(data.uptimeSeconds)}</Row>
              <Row label="Java">{data.javaVersion}</Row>
              <Row label="Fuso">{data.timeZone}</Row>
              <Row label="Memória">{data.memoryUsedMb} de {data.memoryMaxMb} MB</Row>
            </dl>
          </CardContent>
        </Card>
        <Card size="sm">
          <CardHeader><CardTitle>Banco de dados</CardTitle></CardHeader>
          <CardContent>
            <dl className="space-y-2 text-sm">
              <Row label="Situação"><Status ok={data.database.up} on="Respondendo" off="Fora do ar" /></Row>
              {data.database.up && (
                <>
                  <Row label="Resposta">{data.database.latencyMs} ms</Row>
                  <Row label="PostgreSQL">{data.database.version}</Row>
                  <Row label="Tamanho">{formatBytes(data.database.sizeBytes)}</Row>
                  <Row label="Migration">V{data.database.schemaVersion}</Row>
                </>
              )}
            </dl>
          </CardContent>
        </Card>
        <Card size="sm">
          <CardHeader><CardTitle>Recursos</CardTitle></CardHeader>
          <CardContent>
            <dl className="space-y-2 text-sm">
              <Row label="Importação por IA"><Status ok={data.features.ai} on={data.features.aiModel ?? 'Ligada'} off="Desligada" /></Row>
              <Row label="Push no celular"><Status ok={data.features.push} on="Ligado" off="Desligado" /></Row>
              <Row label="Login com Google"><Status ok={data.features.googleLogin} on="Ligado" off="Desligado" /></Row>
              <Row label="Admin do servidor"><Status ok={data.features.fixedAdmin} on="Definido" off="Sem ADMIN_EMAIL" /></Row>
            </dl>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle>Tarefas agendadas</CardTitle></CardHeader>
        <CardContent>
          {!data.jobs.length ? (
            <p className="text-sm text-muted-foreground">Nenhuma tarefa agendada (agendamentos desligados neste ambiente).</p>
          ) : (
            <>
              <Table className="hidden md:table">
                <TableHeader>
                  <TableRow>
                    <TableHead>Tarefa</TableHead>
                    <TableHead>Situação</TableHead>
                    <TableHead>Última vez</TableHead>
                    <TableHead>Próxima</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.jobs.map((job) => (
                    <TableRow key={job.name}>
                      <TableCell className="font-mono text-xs">{job.name}</TableCell>
                      <TableCell><span className="inline-flex items-center gap-1.5">{JOB_ICON[job.status]} {JOB_LABEL[job.status]}</span></TableCell>
                      <TableCell className="text-muted-foreground">{job.lastRun ? formatRelative(job.lastRun) : 'Nunca'}</TableCell>
                      <TableCell className="text-muted-foreground tabular-nums">{job.nextRun ? when(job.nextRun) : '-'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <ul className="divide-y md:hidden">
                {data.jobs.map((job) => (
                  <li key={job.name} className="space-y-1 py-2 text-sm">
                    <p className="truncate font-mono text-xs">{job.name}</p>
                    <p className="flex items-center gap-1.5 text-muted-foreground">
                      {JOB_ICON[job.status]} {JOB_LABEL[job.status]}
                      {job.lastRun && <> · {formatRelative(job.lastRun)}</>}
                    </p>
                  </li>
                ))}
              </ul>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0 truncate text-right font-medium">{children}</dd>
    </div>
  )
}

function Status({ ok, on, off }: { ok: boolean; on: string; off: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      {ok ? <CheckCircle2Icon className="size-4 text-income" aria-hidden /> : <CircleXIcon className="size-4 text-muted-foreground" aria-hidden />}
      {ok ? on : off}
    </span>
  )
}
