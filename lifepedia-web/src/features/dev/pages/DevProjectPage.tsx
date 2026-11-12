import { AlertTriangleIcon, ArrowLeftIcon, ExternalLinkIcon, PencilIcon, PlusIcon, SettingsIcon, Trash2Icon } from 'lucide-react'
import { type ReactNode, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader } from '@/components/PageHeader'
import { ScrollTabsList } from '@/components/ScrollTabsList'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { formatRelative } from '@/lib/format'
import { useDeleteDevProject, useDeleteRepo, useDevProject } from '../api'
import { ActivityHeatmap } from '../components/ActivityHeatmap'
import { DevProjectDialog } from '../components/DevProjectDialog'
import { EventList } from '../components/EventList'
import { ItemRow } from '../components/ItemRow'
import { RepoDialog } from '../components/RepoDialog'
import { RulesDialog } from '../components/RulesDialog'
import { LINK_KIND } from '../labels'
import type { DevItem, DevRepo } from '../types'

const TABS = ['geral', 'repositorios', 'reunioes'] as const
type Tab = (typeof TABS)[number]

/** Página do projeto: atalhos, MRs, revisões, issues e pipelines, repositórios com as raias e as reuniões da semana. */
export function DevProjectPage() {
  const { projectId = '' } = useParams()
  const navigate = useNavigate()
  const { data, isError, error } = useDevProject(projectId)
  const remove = useDeleteDevProject()
  const removeRepo = useDeleteRepo()
  const [params, setParams] = useSearchParams()
  const tab: Tab = TABS.includes(params.get('aba') as Tab) ? (params.get('aba') as Tab) : 'geral'
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [adding, setAdding] = useState(false)
  const [rules, setRules] = useState<DevRepo | null>(null)
  const [unlinking, setUnlinking] = useState<DevRepo | null>(null)

  if (isError) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-destructive">{error instanceof ApiError ? error.message : 'Não foi possível abrir o projeto.'}</p>
        <Button variant="outline" asChild><Link to="/dev"><ArrowLeftIcon /> Voltar para o Dev</Link></Button>
      </div>
    )
  }
  if (!data) return <Skeleton className="h-96" />

  const { project, items, events, activity } = data
  const providerOf = (item: DevItem) => project.repos.find((r) => r.id === item.repoId)?.provider ?? null
  const mine = items.filter((i) => i.kind === 'MR' && i.mine)
  const reviews = items.filter((i) => i.kind === 'MR' && i.review && i.state === 'opened')
  const issues = items.filter((i) => i.kind === 'ISSUE')
  const pipelines = items.filter((i) => i.kind === 'PIPELINE')

  async function deleteProject() {
    try {
      await remove.mutateAsync(project.id)
      toast.success('Projeto excluído.')
      navigate('/dev')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível excluir.')
    }
  }

  async function unlink(repo: DevRepo) {
    try {
      await removeRepo.mutateAsync(repo.id)
      toast.success('Repositório desligado do projeto.')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível desligar.')
    }
  }

  const list = (rows: DevItem[]) => <ul className="divide-y">{rows.map((i) => <ItemRow key={i.id} item={i} provider={providerOf(i)} />)}</ul>

  return (
    <div className="space-y-6">
      <Link to="/dev" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeftIcon className="size-4" aria-hidden /> Dev
      </Link>
      <PageHeader title={project.name} description={project.description ?? undefined}
        actions={[
          { label: 'Ligar repositório', icon: PlusIcon, primary: true, onSelect: () => setAdding(true) },
          { label: 'Editar projeto', icon: PencilIcon, onSelect: () => setEditing(true) },
          { label: 'Excluir projeto', icon: Trash2Icon, secondary: true, destructive: true, onSelect: () => setDeleting(true) },
        ]} />

      {project.links.length > 0 && (
        <nav aria-label="Atalhos do projeto" className="flex flex-wrap gap-2">
          {project.links.map((link) => {
            const Icon = LINK_KIND[link.kind].icon
            return (
              <a key={link.url + link.label} href={link.url} target="_blank" rel="noopener noreferrer"
                className="flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
                <Icon className="size-4" style={{ color: project.color }} aria-hidden />{link.label}
              </a>
            )
          })}
        </nav>
      )}

      <Tabs value={tab} onValueChange={(v) => setParams((p) => { p.set('aba', v); return p }, { replace: true })}>
        <ScrollTabsList value={tab}>
          <TabsTrigger value="geral">Visão geral</TabsTrigger>
          <TabsTrigger value="repositorios">Repositórios ({project.repos.length})</TabsTrigger>
          <TabsTrigger value="reunioes">Reuniões</TabsTrigger>
        </ScrollTabsList>

        <TabsContent value="geral" className="space-y-4 pt-2">
          {project.repos.length === 0 ? (
            <Card>
              <CardContent className="flex flex-col items-center gap-3 py-8 text-center">
                <p className="max-w-md text-sm text-muted-foreground">Ligue um repositório para ver MRs, issues e pipelines aqui.</p>
                <Button onClick={() => setAdding(true)}><PlusIcon /> Ligar repositório</Button>
              </CardContent>
            </Card>
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              <Section title="Seus MRs" count={mine.length} empty="Nenhum MR seu aberto ou aceito há pouco.">{list(mine)}</Section>
              <Section title="Para revisar" count={reviews.length} empty="Ninguém pediu sua revisão.">{list(reviews)}</Section>
              <Section title="Issues atribuídas" count={issues.length} empty="Nenhuma issue aberta com você.">{list(issues)}</Section>
              <Section title="Branch principal" count={pipelines.length} empty="Sem pipelines no branch principal.">{list(pipelines)}</Section>
            </div>
          )}
          <Card>
            <CardHeader><CardTitle className="text-base">Atividade (12 semanas)</CardTitle></CardHeader>
            <CardContent><ActivityHeatmap days={activity} /></CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="repositorios" className="space-y-3 pt-2">
          {project.repos.length === 0 ? <p className="text-sm text-muted-foreground">Nenhum repositório ligado.</p> : (
            <ul className="space-y-3">
              {project.repos.map((repo) => (
                <li key={repo.id}>
                  <Card className="gap-3">
                    <CardContent className="space-y-2">
                      <div className="flex flex-wrap items-start gap-2">
                        <div className="min-w-0 flex-1">
                          <a href={repo.webUrl} target="_blank" rel="noopener noreferrer"
                            className="flex items-center gap-1.5 font-medium underline-offset-2 hover:underline">
                            <span className="truncate">{repo.fullName}</span><ExternalLinkIcon className="size-3.5 shrink-0 opacity-60" aria-hidden />
                          </a>
                          <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                            <span className="size-2 rounded-full" style={{ background: repo.accountColor }} aria-hidden />
                            {repo.accountName}
                            {repo.syncedAt ? ` · sincronizado ${formatRelative(repo.syncedAt)}` : ' · sincronizando'}
                          </p>
                        </div>
                        <Button size="sm" variant="outline" onClick={() => setRules(repo)}><SettingsIcon /> Regras</Button>
                        <Button size="icon" variant="ghost" aria-label={`Desligar ${repo.fullName}`} onClick={() => setUnlinking(repo)}>
                          <Trash2Icon />
                        </Button>
                      </div>
                      {!repo.accountActive && (
                        <p className="flex items-center gap-1.5 text-xs text-destructive">
                          <AlertTriangleIcon className="size-3.5" aria-hidden />
                          A conta parou de sincronizar. <Link to="/integracoes" className="underline underline-offset-2">Reconectar</Link>
                        </p>
                      )}
                      <div className="flex flex-wrap gap-1.5 text-xs">
                        <Badge variant={repo.importIssues ? 'secondary' : 'outline'}>{repo.importIssues ? 'Issues viram tarefas' : 'Issues não viram tarefas'}</Badge>
                        {repo.closeIssues && <Badge variant="secondary">Concluir fecha a issue</Badge>}
                        {repo.labelMrOpened && <Badge variant="outline">MR aberto: {repo.labelMrOpened}</Badge>}
                        {repo.labelMrMerged && <Badge variant="outline">MR aceito: {repo.labelMrMerged}</Badge>}
                        {!repo.labelMrOpened && !repo.labelMrMerged && <Badge variant="outline">Sem raias automáticas</Badge>}
                      </div>
                    </CardContent>
                  </Card>
                </li>
              ))}
            </ul>
          )}
          <Button variant="outline" onClick={() => setAdding(true)}><PlusIcon /> Ligar repositório</Button>
        </TabsContent>

        <TabsContent value="reunioes" className="pt-2">
          <Card>
            <CardHeader><CardTitle className="text-base">Próximos 7 dias</CardTitle></CardHeader>
            <CardContent>
              {!project.calendarAccountId ? (
                <p className="text-sm text-muted-foreground">
                  Escolha a conta Google do projeto em <button type="button" className="underline underline-offset-2" onClick={() => setEditing(true)}>Editar projeto</button>.
                </p>
              ) : events.length === 0 ? <p className="text-sm text-muted-foreground">Nenhuma reunião nos próximos dias.</p>
                : <EventList events={events} showDay />}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <DevProjectDialog open={editing} onOpenChange={setEditing} project={project} />
      <RepoDialog projectId={project.id} open={adding} onOpenChange={setAdding} />
      <RulesDialog repo={rules} onOpenChange={(open) => !open && setRules(null)} />
      <ConfirmDelete open={deleting} onOpenChange={setDeleting} title={`Excluir ${project.name}?`}
        description="Os repositórios ligados saem do projeto. As tarefas criadas pelas issues continuam." onConfirm={() => void deleteProject()} />
      <ConfirmDelete open={unlinking !== null} onOpenChange={(open) => !open && setUnlinking(null)} confirmLabel="Desligar"
        title={`Desligar ${unlinking?.fullName ?? ''}?`} description="MRs, issues e pipelines dele somem do projeto. As tarefas continuam."
        onConfirm={() => { if (unlinking) void unlink(unlinking); setUnlinking(null) }} />
    </div>
  )
}

function Section({ title, count, empty, children }: { title: string; count: number; empty: string; children: ReactNode }) {
  return (
    <Card className="gap-2">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">{title}{count > 0 && <Badge variant="secondary">{count}</Badge>}</CardTitle>
      </CardHeader>
      <CardContent>{count === 0 ? <p className="text-sm text-muted-foreground">{empty}</p> : children}</CardContent>
    </Card>
  )
}
