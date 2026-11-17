import {
  ArchiveIcon,
  ArchiveRestoreIcon,
  ArrowLeftIcon,
  FuelIcon,
  GaugeIcon,
  LogOutIcon,
  PencilIcon,
  RefreshCwIcon,
  Share2Icon,
  Trash2Icon,
  WalletIcon,
} from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDelete } from '@/components/ConfirmDelete'
import { PageHeader, type PageAction } from '@/components/PageHeader'
import { ScrollTabsList } from '@/components/ScrollTabsList'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { formatDate, formatRelative } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { useArchiveVehicle, useDeleteVehicle, useLeaveVehicle, useRefreshFipe, useVehicle } from '../api'
import { CareTab } from '../components/CareTab'
import { ReadingDialog } from '../components/CareDialogs'
import { DocumentsTab } from '../components/DocumentsTab'
import { ExpenseDialog } from '../components/ExpenseDialog'
import { ExpensesTab } from '../components/ExpensesTab'
import { ReadingsTab } from '../components/ReadingsTab'
import { ShareDialog } from '../components/ShareDialog'
import { SummaryTab } from '../components/SummaryTab'
import { VehicleDialog } from '../components/VehicleDialog'
import { VehicleThumb } from '../components/VehicleCard'
import { FUEL_LABEL, ROLE_LABEL, TYPE_LABEL, canAct, formatKm, formatNumber, vehicleTitle } from '../labels'
import type { ExpenseKind } from '../types'

const TABS = ['resumo', 'gastos', 'manutencao', 'documentos', 'km'] as const
type Tab = (typeof TABS)[number]

/** Detalhe do veículo: km, próximos vencimentos, gastos, manutenção, documentos e leituras. */
export function VehiclePage() {
  const { vehicleId = '' } = useParams()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const { formatCurrency } = useMoney()
  const { data, isError, error } = useVehicle(vehicleId)
  const archive = useArchiveVehicle()
  const remove = useDeleteVehicle()
  const leave = useLeaveVehicle()
  const fipe = useRefreshFipe()
  const [editing, setEditing] = useState(false)
  const [sharing, setSharing] = useState(false)
  const [reading, setReading] = useState(false)
  const [expense, setExpense] = useState<{ open: boolean; kind: ExpenseKind }>({ open: false, kind: 'FUEL' })
  const [deleting, setDeleting] = useState(false)
  const tab: Tab = TABS.includes(params.get('aba') as Tab) ? (params.get('aba') as Tab) : 'resumo'

  if (isError) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-destructive">{error instanceof ApiError ? error.message : 'Não foi possível abrir o veículo.'}</p>
        <Button variant="outline" asChild><Link to="/veiculos"><ArrowLeftIcon /> Voltar para Veículos</Link></Button>
      </div>
    )
  }
  if (!data) return <Skeleton className="h-96" />

  const v = data.vehicle
  const canLog = canAct(v.role, 'LOG')
  const canEdit = canAct(v.role, 'EDIT')
  const owner = v.role === 'OWNER'

  async function run(action: () => Promise<unknown>, done: string) {
    try {
      await action()
      toast.success(done)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Não foi possível concluir.')
    }
  }

  const actions: PageAction[] = [
    ...(canLog ? [
      { label: 'Atualizar km', icon: GaugeIcon, primary: true, onSelect: () => setReading(true) },
      { label: 'Abastecer', icon: FuelIcon, onSelect: () => setExpense({ open: true, kind: 'FUEL' }) },
      { label: 'Lançar gasto', icon: WalletIcon, onSelect: () => setExpense({ open: true, kind: 'SERVICE' }) },
    ] : []),
    ...(canEdit ? [{ label: 'Editar', icon: PencilIcon, secondary: true, onSelect: () => setEditing(true) }] : []),
    ...(canEdit && data.fipe ? [{ label: 'Atualizar valor da FIPE', icon: RefreshCwIcon, secondary: true,
      onSelect: () => void run(() => fipe.mutateAsync(v.id), 'Valor da FIPE atualizado.') }] : []),
    ...(owner ? [
      { label: 'Compartilhar', icon: Share2Icon, secondary: true, onSelect: () => setSharing(true) },
      v.archivedAt
        ? { label: 'Desarquivar', icon: ArchiveRestoreIcon, secondary: true,
          onSelect: () => void run(() => archive.mutateAsync({ id: v.id, archived: false }), 'Veículo de volta.') }
        : { label: 'Arquivar (vendi/parado)', icon: ArchiveIcon, secondary: true,
          onSelect: () => void run(() => archive.mutateAsync({ id: v.id, archived: true }), 'Veículo arquivado. Os avisos param.') },
      { label: 'Excluir', icon: Trash2Icon, secondary: true, destructive: true, onSelect: () => setDeleting(true) },
    ] : !v.sharedVia ? [{ label: 'Sair do compartilhamento', icon: LogOutIcon, secondary: true,
      onSelect: () => void run(async () => { await leave.mutateAsync(v.id); navigate('/veiculos') }, 'Você saiu do veículo.') }] : []),
  ]

  const title = vehicleTitle(v)
  const description = [TYPE_LABEL[v.type].label, title, v.plate].filter(Boolean).join(' · ')

  return (
    <div className="space-y-6">
      <Link to="/veiculos" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeftIcon className="size-4" aria-hidden /> Veículos
      </Link>
      <PageHeader title={v.name} description={description} actions={actions} hideValues />

      <Card>
        <CardContent className="flex flex-col gap-4 sm:flex-row sm:items-center">
          <VehicleThumb vehicle={v} className="size-20" />
          <dl className="grid flex-1 grid-cols-2 gap-3 text-sm xl:grid-cols-4">
            <Info label="Km hoje (estimado)">{formatKm(v.estimatedKm)}</Info>
            <Info label="Última leitura">{v.odometerOn ? `${formatKm(v.odometer)} em ${formatDate(v.odometerOn)}` : '-'}</Info>
            <Info label="Ritmo">{v.kmPerDay != null ? `${formatNumber(v.kmPerDay)} km/dia` : '-'}</Info>
            <Info label="Tabela FIPE">
              {v.fipeValue != null ? formatCurrency(v.fipeValue) : data.fipe ? 'Sem valor ainda' : 'Não ligado'}
            </Info>
          </dl>
        </CardContent>
        {(!v.mine || data.fipe?.month || v.fuel || data.notes) && (
          <CardContent className="space-y-1 pt-0 text-xs text-muted-foreground">
            {!v.mine && v.owner && (
              <p>De {v.owner.name}{v.sharedVia ? ` (pelo grupo ${v.sharedVia})` : ''}. Você: {ROLE_LABEL[v.role].toLowerCase()}.</p>
            )}
            {data.fipe?.month && <p>FIPE de {data.fipe.month}{data.fipe.checkedAt ? `, consultada ${formatRelative(data.fipe.checkedAt)}` : ''}.</p>}
            {(v.fuel || data.tankLiters) && (
              <p>{[v.fuel ? FUEL_LABEL[v.fuel] : null, data.tankLiters ? `tanque de ${formatNumber(data.tankLiters)} L` : null].filter(Boolean).join(', ')}</p>
            )}
            {data.notes && <p className="whitespace-pre-line">{data.notes}</p>}
          </CardContent>
        )}
      </Card>

      <Tabs value={tab} onValueChange={(t) => setParams((p) => { p.set('aba', t); return p }, { replace: true })}>
        <ScrollTabsList value={tab}>
          <TabsTrigger value="resumo">Resumo{v.alerts ? ` (${v.alerts})` : ''}</TabsTrigger>
          <TabsTrigger value="gastos">Gastos</TabsTrigger>
          <TabsTrigger value="manutencao">Manutenção</TabsTrigger>
          <TabsTrigger value="documentos">Documentos</TabsTrigger>
          <TabsTrigger value="km">Km</TabsTrigger>
        </ScrollTabsList>
        <TabsContent value="resumo" className="pt-2"><SummaryTab detail={data} /></TabsContent>
        <TabsContent value="gastos" className="pt-2"><ExpensesTab vehicle={v} /></TabsContent>
        <TabsContent value="manutencao" className="pt-2"><CareTab detail={data} /></TabsContent>
        <TabsContent value="documentos" className="pt-2"><DocumentsTab detail={data} /></TabsContent>
        <TabsContent value="km" className="pt-2"><ReadingsTab vehicle={v} onAdd={() => setReading(true)} /></TabsContent>
      </Tabs>

      <ReadingDialog open={reading} onOpenChange={setReading} vehicle={v} />
      <ExpenseDialog open={expense.open} onOpenChange={(open) => setExpense((s) => ({ ...s, open }))} vehicle={v} kind={expense.kind} />
      {canEdit && <VehicleDialog open={editing} onOpenChange={setEditing} detail={data} />}
      {owner && (
        <>
          <ShareDialog open={sharing} onOpenChange={setSharing} vehicleId={v.id} />
          <ConfirmDelete open={deleting} onOpenChange={setDeleting} title={`Excluir ${v.name}?`}
            description="Km, gastos, manutenção e documentos saem do LifePedia. O que já foi para o financeiro fica lá. Para guardar o histórico, use Arquivar."
            onConfirm={() => void run(async () => { await remove.mutateAsync(v.id); navigate('/veiculos') }, 'Veículo excluído.')} />
        </>
      )}
    </div>
  )
}

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-xl bg-muted/50 px-3 py-2">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="font-medium">{children}</dd>
    </div>
  )
}
