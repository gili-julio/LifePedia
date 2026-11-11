import { Building2Icon } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatInstantDate } from '@/lib/format'
import { usePublicProject } from '../api'
import { ModelStage } from '../components/ModelStage'
import { versionName } from '../labels'

/** Link público da maquete (`/maquete/:token`): só a maquete atual e as vistas, sem login e sem o app em volta. */
export function PublicModelPage() {
  const { token = '' } = useParams()
  const { data, error } = usePublicProject(token)

  return (
    <div className="min-h-dvh bg-background">
      <header className="flex items-center justify-between gap-3 border-b px-4 py-3">
        <Link to="/" aria-label="LifePedia"><Logo /></Link>
        <Button asChild size="sm" variant="outline"><Link to="/login">Entrar</Link></Button>
      </header>
      <main className="mx-auto max-w-6xl space-y-4 p-4">
        {error ? (
          <div className="flex flex-col items-center gap-3 py-20 text-center">
            <Building2Icon className="size-8 text-muted-foreground" aria-hidden />
            <p className="text-sm text-muted-foreground">Este link não existe mais ou venceu. Peça um novo para quem compartilhou.</p>
          </div>
        ) : !data ? <Skeleton className="h-[70vh]" /> : (
          <>
            <div className="space-y-1">
              <h1 className="text-2xl font-semibold tracking-tight">{data.name}</h1>
              <p className="text-sm text-muted-foreground">
                {versionName(data.version)}{data.ownerName ? ` · compartilhado por ${data.ownerName}` : ''} · link vale até{' '}
                {formatInstantDate(data.expiresAt)}
              </p>
              {data.description && <p className="text-sm">{data.description}</p>}
            </div>
            <ModelStage source={data.version.modelUrl} latitude={data.latitude} north={data.northDegrees} views={data.views}
              scene={data.scene} className="h-[75dvh]" />
          </>
        )}
      </main>
    </div>
  )
}
