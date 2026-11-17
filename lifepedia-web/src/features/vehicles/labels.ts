import { BikeIcon, CarIcon, CircleHelpIcon, type LucideIcon, MotorbikeIcon, TruckIcon } from 'lucide-react'
import { formatDate } from '@/lib/format'
import type { Due, DocumentKind, ExpenseKind, FipeKind, Stage, VehicleFuel, VehicleRole, VehicleType } from './types'

export const TYPES: VehicleType[] = ['CAR', 'MOTORCYCLE', 'TRUCK', 'BICYCLE', 'OTHER']

export const TYPE_LABEL: Record<VehicleType, { label: string; icon: LucideIcon }> = {
  CAR: { label: 'Carro', icon: CarIcon },
  MOTORCYCLE: { label: 'Moto', icon: MotorbikeIcon },
  TRUCK: { label: 'Caminhão', icon: TruckIcon },
  BICYCLE: { label: 'Bicicleta', icon: BikeIcon },
  OTHER: { label: 'Outro', icon: CircleHelpIcon },
}

/** Espelha `VehicleType.fipe` da API. */
export const FIPE_KIND: Record<VehicleType, FipeKind | null> = {
  CAR: 'CARS', MOTORCYCLE: 'MOTORCYCLES', TRUCK: 'TRUCKS', BICYCLE: null, OTHER: null,
}

export const FUELS: VehicleFuel[] = ['FLEX', 'GASOLINE', 'ETHANOL', 'DIESEL', 'GNV', 'ELECTRIC', 'HYBRID']

export const FUEL_LABEL: Record<VehicleFuel, string> = {
  GASOLINE: 'Gasolina',
  ETHANOL: 'Etanol',
  FLEX: 'Flex',
  DIESEL: 'Diesel',
  GNV: 'GNV',
  ELECTRIC: 'Elétrico',
  HYBRID: 'Híbrido',
}

export const SHARE_ROLES: Exclude<VehicleRole, 'OWNER'>[] = ['VIEW', 'LOG', 'EDIT']

export const ROLE_LABEL: Record<VehicleRole, string> = {
  VIEW: 'Pode ver',
  LOG: 'Pode lançar',
  EDIT: 'Pode editar',
  OWNER: 'Dono',
}

export const ROLE_HINT: Record<Exclude<VehicleRole, 'OWNER'>, string> = {
  VIEW: 'Vê km, gastos, manutenção e documentos, sem mudar nada.',
  LOG: 'Também atualiza o km, lança abastecimentos e gastos e marca manutenção feita.',
  EDIT: 'Também muda os dados do veículo, os itens de manutenção, as revisões e os documentos.',
}

/** Papel mínimo para a ação (a API confere de novo). */
export function canAct(role: VehicleRole, needed: VehicleRole) {
  const order: VehicleRole[] = ['VIEW', 'LOG', 'EDIT', 'OWNER']
  return order.indexOf(role) >= order.indexOf(needed)
}

export const EXPENSE_KINDS: ExpenseKind[] = ['FUEL', 'SERVICE', 'PART', 'TAX', 'LICENSING', 'INSURANCE', 'FINE', 'TOLL', 'WASH', 'OTHER']

export const EXPENSE_LABEL: Record<ExpenseKind, string> = {
  FUEL: 'Abastecimento',
  SERVICE: 'Manutenção / oficina',
  PART: 'Peças',
  TAX: 'IPVA',
  LICENSING: 'Licenciamento',
  INSURANCE: 'Seguro',
  FINE: 'Multa',
  TOLL: 'Pedágio e estacionamento',
  WASH: 'Lavagem',
  OTHER: 'Outro gasto',
}

export const DOCUMENT_KINDS: DocumentKind[] = ['IPVA', 'LICENSING', 'INSURANCE', 'CNH', 'INSPECTION', 'OTHER']

export const DOCUMENT_LABEL: Record<DocumentKind, string> = {
  IPVA: 'IPVA',
  LICENSING: 'Licenciamento',
  INSURANCE: 'Seguro',
  CNH: 'CNH',
  INSPECTION: 'Vistoria',
  OTHER: 'Outro',
}

/**
 * Grupos dos gráficos (espelha `ExpenseKind.Group`), na ordem fixa da paleta categórica validada
 * (azul, laranja, verde-água, amarelo; ver o gráfico de bancos).
 */
export const GROUPS = [
  { key: 'fuel', label: 'Combustível', color: '#2a78d6' },
  { key: 'maintenance', label: 'Manutenção', color: '#eb6834' },
  { key: 'documents', label: 'Documentos e taxas', color: '#1baf7a' },
  { key: 'other', label: 'Outros', color: '#eda100' },
] as const

export const STAGE_LABEL: Record<Stage, string> = { OK: 'Em dia', SOON: 'Chegando', DUE: 'Passou da hora' }

const KM = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })
const DECIMAL = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 2 })

export function formatKm(km: number) {
  return `${KM.format(km)} km`
}

export function formatNumber(value: number) {
  return DECIMAL.format(value)
}

/** "a cada 10.000 km ou 12 meses". */
export function intervalText(km: number | null, months: number | null) {
  const parts = [km ? formatKm(km) : null, months ? `${months} ${months === 1 ? 'mês' : 'meses'}` : null].filter(Boolean)
  return parts.length ? `a cada ${parts.join(' ou ')}` : ''
}

/** "faltam 800 km · por volta de 12/03/2027" / "passou 200 km". */
export function dueText(due: Due) {
  const parts: string[] = []
  if (due.kmLeft != null) parts.push(due.kmLeft > 0 ? `faltam ${formatKm(due.kmLeft)}` : `passou ${formatKm(-due.kmLeft)}`)
  if (due.expectedOn) {
    const late = due.daysLeft != null && due.daysLeft < 0
    parts.push(due.kmLeft != null && due.dueOn !== due.expectedOn ? `por volta de ${formatDate(due.expectedOn)}`
      : `${late ? 'era' : 'em'} ${formatDate(due.expectedOn)}`)
  }
  return parts.join(' · ')
}

export function vehicleTitle(v: { brand: string | null; model: string | null; modelYear: number | null }) {
  return [v.brand, v.model, v.modelYear].filter(Boolean).join(' ')
}
