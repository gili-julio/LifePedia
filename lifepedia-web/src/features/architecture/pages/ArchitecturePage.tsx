import { Building2Icon, PlusIcon, UsersIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { UserAvatar } from '@/components/UserAvatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatRelative } from '@/lib/format'
import { useProjects } from '../api'
import { ProjectFormDialog } from '../components/ProjectFormDialog'
import { UsageBar } from '../components/UsageBar'
import { projectUrl, ROLE_LABEL, versionName } from '../labels'
import type { ProjectSummary } from '../types'

/** Projetos de arquitetura: os da pessoa e os compartilhados com ela (ordem da API: mexidos por último primeiro). */
export function ArchitecturePage() {
  const navigate = useNavigate()
  const { data } = useProjects()
  const [creating, setCreating] = useState(false)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Arquitetura"
        description="Maquetes 3D da casa: ande por dentro, meça, comente e compartilhe."
        actions={[{ label: 'Novo projeto', icon: PlusIcon, primary: true, onSelect: () => setCreating(true) }]}
      />

      {!data ? <Skeleton className="h-56" /> : (
        <>
          <section aria-labelledby="arch-mine" className="space-y-3">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <h2 id="arch-mine" className="text-lg font-semibold tracking-tight">Meus projetos</h2>
              <div className="w-full sm:w-72"><UsageBar usage={data.usage} /></div>
            </div>
            {data.owned.length === 0 ? (
              <Card>
                <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
                  <Building2Icon className="size-8 text-muted-foreground" aria-hidden />
                  <p className="max-w-md text-sm text-muted-foreground">
                    Crie um projeto e envie a maquete exportada do SketchUp. Ela é comprimida no seu navegador antes de subir.
                  </p>
                  <Button onClick={() => setCreating(true)}><PlusIcon /> Novo projeto</Button>
                </CardContent>
              </Card>
            ) : <ProjectGrid items={data.owned} />}
          </section>

          {data.shared.length > 0 && (
            <section aria-labelledby="arch-shared" className="space-y-3">
              <h2 id="arch-shared" className="text-lg font-semibold tracking-tight">Compartilhados comigo</h2>
              <ProjectGrid items={data.shared} />
            </section>
          )}
        </>
      )}

      <ProjectFormDialog open={creating} onOpenChange={setCreating} onSaved={(p) => navigate(projectUrl(p.id))} />
    </div>
  )
}

function ProjectGrid({ items }: { items: ProjectSummary[] }) {
  return (
    <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {items.map((p) => (
        <li key={p.id}>
          <Link to={projectUrl(p.id)} className="group block rounded-2xl focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
            <Card className="overflow-hidden py-0 transition-shadow group-hover:shadow-md motion-reduce:transition-none">
              <div className="aspect-video bg-muted">
                {p.coverUrl ? <img src={p.coverUrl} alt="" loading="lazy" className="size-full object-cover" /> : (
                  <div className="flex size-full items-center justify-center text-muted-foreground">
                    <Building2Icon className="size-10" aria-hidden />
                  </div>
                )}
              </div>
              <CardContent className="space-y-2 py-4">
                <div className="flex items-center gap-2">
                  <p className="min-w-0 flex-1 truncate font-medium">{p.name}</p>
                  {p.role !== 'OWNER' && <Badge variant="secondary" className="shrink-0">{ROLE_LABEL[p.role]}</Badge>}
                </div>
                <p className="truncate text-xs text-muted-foreground">
                  {p.currentVersion ? versionName(p.currentVersion) : 'Sem maquete ainda'} · {formatRelative(p.updatedAt)}
                </p>
                {p.role !== 'OWNER' && (
                  <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    {p.sharedVia ? <><UsersIcon className="size-3.5" aria-hidden /> Pelo grupo {p.sharedVia}</>
                      : <><UserAvatar user={p.owner} size="xs" /> De {p.owner.name}</>}
                  </p>
                )}
              </CardContent>
            </Card>
          </Link>
        </li>
      ))}
    </ul>
  )
}
