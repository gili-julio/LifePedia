import { ArrowLeftIcon, Building2Icon, LogOutIcon, PencilIcon, Share2Icon, Trash2Icon, UploadIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader, type PageAction } from '@/components/PageHeader'
import { ScrollTabsList } from '@/components/ScrollTabsList'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { useDeleteProject, useLeaveProject, useProject } from '../api'
import { AttachmentsTab } from '../components/AttachmentsTab'
import { ModelTab } from '../components/ModelTab'
import { NewVersionDialog } from '../components/NewVersionDialog'
import { ProjectFormDialog } from '../components/ProjectFormDialog'
import { ShareDialog } from '../components/ShareDialog'
import { VersionsTab } from '../components/VersionsTab'
import { ROLE_LABEL, versionName } from '../labels'
import { canAct } from '../types'

const TABS = ['maquete', 'versoes', 'anexos'] as const
type Tab = (typeof TABS)[number]

/** Projeto: maquete (com comentários), versões e anexos; `?aba=` e `?versao=` na URL. */
export function ProjectPage() {
  const { projectId = '' } = useParams()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const { data: project, error } = useProject(projectId)
  const removeProject = useDeleteProject()
  const leave = useLeaveProject()
  const [dialog, setDialog] = useState<'version' | 'share' | 'edit' | 'delete' | 'leave' | null>(null)

  const aba = params.get('aba')
  const tab: Tab = TABS.includes(aba as Tab) ? (aba as Tab) : 'maquete'

  if (error) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
          <p className="text-sm text-muted-foreground">
            {error instanceof ApiError && error.status === 404 ? 'Projeto não encontrado ou sem acesso.' : 'Não foi possível abrir o projeto.'}
          </p>
          <Button asChild variant="outline"><Link to="/arquitetura"><ArrowLeftIcon /> Voltar para Arquitetura</Link></Button>
        </CardContent>
      </Card>
    )
  }
  if (!project) return <Skeleton className="h-[70vh]" />

  const versionId = project.versions.some((v) => v.id === params.get('versao')) ? params.get('versao')! : project.currentVersionId
  const version = project.versions.find((v) => v.id === versionId) ?? null
  const canEdit = canAct(project.role, 'EDIT')
  const isOwner = project.role === 'OWNER'

  const change = (next: Record<string, string | null>) => {
    const merged = new URLSearchParams(params)
    Object.entries(next).forEach(([k, v]) => (v ? merged.set(k, v) : merged.delete(k)))
    setParams(merged, { replace: true })
  }

  const actions: PageAction[] = [
    ...(canEdit ? [{ label: 'Nova versão', icon: UploadIcon, primary: true, onSelect: () => setDialog('version') }] : []),
    ...(isOwner ? [{ label: 'Compartilhar', icon: Share2Icon, onSelect: () => setDialog('share') }] : []),
    ...(canEdit ? [{ label: 'Editar projeto', icon: PencilIcon, secondary: true, onSelect: () => setDialog('edit') }] : []),
    ...(!isOwner ? [{ label: 'Sair do projeto', icon: LogOutIcon, secondary: true, onSelect: () => setDialog('leave') }] : []),
    ...(isOwner ? [{ label: 'Excluir projeto', icon: Trash2Icon, secondary: true, destructive: true,
      onSelect: () => setDialog('delete') }] : []),
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title={project.name}
        description={isOwner ? (project.description ?? undefined)
          : `${ROLE_LABEL[project.role]} · projeto de ${project.owner.name}${project.description ? `. ${project.description}` : ''}`}
        actions={actions}
        toolbar={project.versions.length > 1 && version ? (
          <Select value={version.id} onValueChange={(v) => change({ versao: v === project.currentVersionId ? null : v })}>
            <SelectTrigger className="w-full sm:w-72" aria-label="Versão"><SelectValue /></SelectTrigger>
            <SelectContent>
              {project.versions.map((v) => (
                <SelectItem key={v.id} value={v.id}>{versionName(v)}{v.id === project.currentVersionId ? ' (atual)' : ''}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : undefined}
      />

      <Tabs value={tab} onValueChange={(v) => change({ aba: v === 'maquete' ? null : v })}>
        <ScrollTabsList value={tab}>
          <TabsTrigger value="maquete">Maquete</TabsTrigger>
          <TabsTrigger value="versoes">Versões ({project.versions.length})</TabsTrigger>
          <TabsTrigger value="anexos">Anexos ({project.attachments})</TabsTrigger>
        </ScrollTabsList>
        <TabsContent value="maquete" className="pt-4">
          {version ? <ModelTab key={version.id} project={project} version={version} /> : (
            <Card>
              <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
                <Building2Icon className="size-8 text-muted-foreground" aria-hidden />
                <p className="max-w-md text-sm text-muted-foreground">
                  {canEdit ? 'Envie a maquete exportada do SketchUp (.dae ou .glb) para ver a casa em 3D.'
                    : 'Este projeto ainda não tem maquete.'}
                </p>
                {canEdit && <Button onClick={() => setDialog('version')}><UploadIcon /> Enviar a maquete</Button>}
              </CardContent>
            </Card>
          )}
        </TabsContent>
        <TabsContent value="versoes" className="pt-4">
          <VersionsTab project={project} onOpen={(id) => change({ aba: null, versao: id === project.currentVersionId ? null : id })} />
        </TabsContent>
        <TabsContent value="anexos" className="pt-4">
          <AttachmentsTab project={project} />
        </TabsContent>
      </Tabs>

      {canEdit && <NewVersionDialog open={dialog === 'version'} onOpenChange={(o) => {
        setDialog(o ? 'version' : null)
        if (!o) change({ versao: null })
      }} project={project} />}
      {isOwner && <ShareDialog open={dialog === 'share'} onOpenChange={(o) => setDialog(o ? 'share' : null)} projectId={project.id} />}
      <ProjectFormDialog open={dialog === 'edit'} onOpenChange={(o) => setDialog(o ? 'edit' : null)} project={project} />
      <ConfirmDelete open={dialog === 'delete'} onOpenChange={(o) => setDialog(o ? 'delete' : null)}
        title="Excluir o projeto?"
        description="Todas as versões, comentários, vistas, anexos e links somem para sempre, também para quem recebeu."
        onConfirm={() => removeProject.mutate(project.id, {
          onSuccess: () => {
            toast.success('Projeto excluído.')
            navigate('/arquitetura')
          },
        })} />
      <ConfirmDelete open={dialog === 'leave'} onOpenChange={(o) => setDialog(o ? 'leave' : null)} confirmLabel="Sair"
        title="Sair do projeto?"
        description="Ele some da sua lista. Para voltar, o dono precisa compartilhar de novo."
        onConfirm={() => leave.mutate(project.id, {
          onSuccess: () => navigate('/arquitetura'),
          onError: () => toast.error('Este projeto chegou por um grupo: só o dono pode tirar o acesso.'),
        })} />
    </div>
  )
}
