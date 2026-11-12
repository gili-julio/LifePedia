import { AlertTriangleIcon, CodeXmlIcon, PlugZapIcon, PlusIcon, SparklesIcon } from 'lucide-react'
import { type ReactNode, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { PROVIDERS } from '@/features/integrations/providers'
import { useDevActivity, useDevProjects, useDevToday } from '../api'
import { ActivityHeatmap } from '../components/ActivityHeatmap'
import { DailyDialog } from '../components/DailyDialog'
import { DevProjectDialog } from '../components/DevProjectDialog'
import { EventList } from '../components/EventList'
import { ItemRow } from '../components/ItemRow'
import { devProjectUrl, LINK_KIND } from '../labels'
import type { DevItem, DevProject } from '../types'

/** Módulo Dev: o painel do dia, os projetos de trabalho e a atividade das últimas semanas. */
export function DevPage() {
  const navigate = useNavigate()
  const projects = useDevProjects()
  const today = useDevToday()
  const activity = useDevActivity(12)
  const [creating, setCreating] = useState(false)
  const [daily, setDaily] = useState(false)
  const empty = projects.data?.length === 0

  return (
    <div className="space-y-6">
      <PageHeader title="Dev" description="Seus projetos de trabalho: reuniões, MRs, revisões, pipelines e issues num lugar só."
        actions={[
          { label: 'Novo projeto', icon: PlusIcon, primary: true, onSelect: () => setCreating(true) },
          ...(today.data?.aiEnabled && !empty ? [{ label: 'Resumo do daily', icon: SparklesIcon, onSelect: () => setDaily(true) }] : []),
          { label: 'Integrações', icon: PlugZapIcon, to: '/integracoes', secondary: true },
        ]} />

      {today.data && today.data.problems.length > 0 && (
        <div role="alert" className="flex flex-wrap items-center gap-2 rounded-2xl border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm">
          <AlertTriangleIcon className="size-4 text-destructive" aria-hidden />
          <span className="flex-1">
            {today.data.problems.map((p) => `${p.nickname} (${PROVIDERS[p.provider].label})`).join(', ')}{' '}
            {today.data.problems.length === 1 ? 'parou' : 'pararam'} de sincronizar.
          </span>
          <Button asChild size="sm" variant="outline"><Link to="/integracoes">Reconectar</Link></Button>
        </div>
      )}

      {!projects.data ? <Skeleton className="h-64" /> : empty ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
            <CodeXmlIcon className="size-8 text-muted-foreground" aria-hidden />
            <p className="max-w-md text-sm text-muted-foreground">
              Crie um projeto para cada trabalho (ex.: Sebito, Gertec, Nut), ligue as contas do GitLab, GitHub e Google em
              Integrações e escolha os repositórios de cada um.
            </p>
            <div className="flex flex-wrap justify-center gap-2">
              <Button onClick={() => setCreating(true)}><PlusIcon /> Novo projeto</Button>
              <Button variant="outline" asChild><Link to="/integracoes"><PlugZapIcon /> Ligar contas</Link></Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <>
          <section aria-labelledby="dev-today" className="space-y-3">
            <h2 id="dev-today" className="text-lg font-semibold tracking-tight">Hoje no dev</h2>
            {!today.data ? <Skeleton className="h-48" /> : (
              <div className="grid gap-4 lg:grid-cols-2">
                <Panel title="Reuniões de hoje" count={today.data.events.length}
                  empty="Nenhuma reunião hoje (ou o Google Agenda não está ligado).">
                  <EventList events={today.data.events} />
                </Panel>
                <ItemsPanel title="Esperando revisão sua" items={today.data.reviews} empty="Ninguém pediu sua revisão." />
                <ItemsPanel title="Seus MRs abertos" items={today.data.myMergeRequests} empty="Nenhum MR aberto." />
                <ItemsPanel title="Pipelines quebradas" items={today.data.failing} empty="Tudo verde." tone="danger" />
                <div className="lg:col-span-2">
                  <ItemsPanel title="Issues atribuídas a você" items={today.data.issues} empty="Nenhuma issue aberta com você." />
                </div>
              </div>
            )}
          </section>

          <section aria-labelledby="dev-projects" className="space-y-3">
            <h2 id="dev-projects" className="text-lg font-semibold tracking-tight">Projetos</h2>
            <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {projects.data.map((p) => <li key={p.id}><ProjectCard project={p} /></li>)}
            </ul>
          </section>

          <section aria-labelledby="dev-activity" className="space-y-3">
            <h2 id="dev-activity" className="text-lg font-semibold tracking-tight">Atividade das últimas 12 semanas</h2>
            <Card>
              <CardContent className="space-y-4">
                {!activity.data ? <Skeleton className="h-40" /> : (
                  <>
                    <ActivityHeatmap days={activity.data.days} />
                    {activity.data.projects.length > 1 && (
                      <ul className="divide-y text-sm">
                        {activity.data.projects.map((p) => (
                          <li key={p.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2">
                            <span className="flex min-w-0 flex-1 items-center gap-2 font-medium">
                              <span className="size-2.5 rounded-full" style={{ background: p.color }} aria-hidden />{p.name}
                            </span>
                            <span className="text-xs text-muted-foreground tabular-nums">
                              {p.commits} commits · {p.mergeRequests} MRs · {p.merged} aceitos · {p.reviews} revisões
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                    <p className="text-xs text-muted-foreground">Contado pelos eventos do GitLab/GitHub nos repositórios ligados.</p>
                  </>
                )}
              </CardContent>
            </Card>
          </section>
        </>
      )}

      <DevProjectDialog open={creating} onOpenChange={setCreating} onSaved={(p) => navigate(devProjectUrl(p.id))} />
      <DailyDialog open={daily} onOpenChange={setDaily} />
    </div>
  )
}

function Panel({ title, count, empty, tone, children }: { title: string; count: number; empty: string; tone?: 'danger'; children: ReactNode }) {
  return (
    <Card className="gap-2">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          {title}
          {count > 0 && <Badge variant={tone === 'danger' ? 'destructive' : 'secondary'}>{count}</Badge>}
        </CardTitle>
      </CardHeader>
      <CardContent>{count === 0 ? <p className="text-sm text-muted-foreground">{empty}</p> : children}</CardContent>
    </Card>
  )
}

function ItemsPanel({ title, items, empty, tone }: { title: string; items: DevItem[]; empty: string; tone?: 'danger' }) {
  return (
    <Panel title={title} count={items.length} empty={empty} tone={tone}>
      <ul className="divide-y">
        {items.slice(0, 8).map((item) => <ItemRow key={item.id} item={item} showProject />)}
      </ul>
      {items.length > 8 && <p className="pt-2 text-xs text-muted-foreground">E mais {items.length - 8} nos projetos.</p>}
    </Panel>
  )
}

function ProjectCard({ project }: { project: DevProject }) {
  const c = project.counts
  return (
    <Card className="h-full gap-3 overflow-hidden pt-0">
      <div className="h-1.5" style={{ background: project.color }} aria-hidden />
      <CardHeader>
        <CardTitle>
          <Link to={devProjectUrl(project.id)} className="underline-offset-2 hover:underline focus-visible:underline focus-visible:outline-none">
            {project.name}
          </Link>
        </CardTitle>
        {project.description && <p className="text-sm text-muted-foreground">{project.description}</p>}
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="flex flex-wrap gap-1.5 text-xs">
          <Badge variant="secondary">{c.myMergeRequests} MRs seus</Badge>
          <Badge variant="secondary">{c.reviews} para revisar</Badge>
          <Badge variant="secondary">{c.issues} issues</Badge>
          {c.failing > 0 && <Badge variant="destructive">{c.failing} quebrada{c.failing > 1 ? 's' : ''}</Badge>}
        </p>
        <p className="text-xs text-muted-foreground">
          {project.repos.length === 0 ? 'Nenhum repositório ligado' : `${project.repos.length} repositório${project.repos.length > 1 ? 's' : ''}`}
        </p>
        {project.links.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {project.links.slice(0, 4).map((link) => {
              const Icon = LINK_KIND[link.kind].icon
              return (
                <a key={link.url + link.label} href={link.url} target="_blank" rel="noopener noreferrer"
                  className="flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
                  <Icon className="size-3.5" aria-hidden />{link.label}
                </a>
              )
            })}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
