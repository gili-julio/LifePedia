import { useSearchParams } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { ScrollTabsList } from '@/components/ScrollTabsList'
import { Card, CardContent } from '@/components/ui/card'
import { Tabs, TabsContent, TabsTrigger } from '@/components/ui/tabs'
import { AuditTab } from '../components/AuditTab'
import { MessagesTab } from '../components/MessagesTab'
import { OverviewTab } from '../components/OverviewTab'
import { SystemTab } from '../components/SystemTab'
import { UsersTab } from '../components/UsersTab'

const TABS = ['visao', 'usuarios', 'avisos', 'auditoria', 'sistema'] as const
type Tab = (typeof TABS)[number]

/** Painel de administração (`/admin`, só admins). */
export function AdminPage() {
  const [params, setParams] = useSearchParams()
  const aba = params.get('aba')
  const tab: Tab = TABS.includes(aba as Tab) ? (aba as Tab) : 'visao'

  return (
    <div className="space-y-6">
      <PageHeader title="Administração" description="Números do sistema, usuários, avisos e a situação do servidor." />
      <Tabs value={tab} onValueChange={(v) => setParams(v === 'visao' ? {} : { aba: v }, { replace: true })}>
        <ScrollTabsList value={tab}>
          <TabsTrigger value="visao">Visão geral</TabsTrigger>
          <TabsTrigger value="usuarios">Usuários</TabsTrigger>
          <TabsTrigger value="avisos">Avisos</TabsTrigger>
          <TabsTrigger value="auditoria">Auditoria</TabsTrigger>
          <TabsTrigger value="sistema">Sistema</TabsTrigger>
        </ScrollTabsList>
        <TabsContent value="visao" className="pt-4"><OverviewTab /></TabsContent>
        <TabsContent value="usuarios" className="pt-4">
          <Card><CardContent><UsersTab /></CardContent></Card>
        </TabsContent>
        <TabsContent value="avisos" className="pt-4"><MessagesTab /></TabsContent>
        <TabsContent value="auditoria" className="pt-4">
          <Card><CardContent><AuditTab /></CardContent></Card>
        </TabsContent>
        <TabsContent value="sistema" className="pt-4"><SystemTab /></TabsContent>
      </Tabs>
    </div>
  )
}
