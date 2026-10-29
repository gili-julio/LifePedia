import { PageHeader } from '@/components/PageHeader'
import { RecentChanges } from '../components/RecentChanges'

/** Todas as mudanças do sistema ("Ver todas" do Início). */
export function ChangelogPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Mudanças recentes no sistema" description="O que mudou no LifePedia, da novidade mais recente para a mais antiga." />
      <div className="max-w-3xl">
        <RecentChanges />
      </div>
    </div>
  )
}
