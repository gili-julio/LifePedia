import { CircleCheckBigIcon, PackageIcon, TruckIcon, WarehouseIcon, type LucideIcon } from 'lucide-react'
import type { Delivery, DeliveryAddress, DeliveryStatus, ManualStatus } from './types'

/** Etapas da barra (índice = `stage` da API). */
export const STAGES: { label: string; icon: LucideIcon }[] = [
  { label: 'Postado', icon: PackageIcon },
  { label: 'Em trânsito', icon: WarehouseIcon },
  { label: 'Saiu para entrega', icon: TruckIcon },
  { label: 'Entregue', icon: CircleCheckBigIcon },
]

export const STATUS_LABEL: Record<DeliveryStatus, string> = {
  PENDING: 'Aguardando informações',
  POSTED: 'Postado',
  IN_TRANSIT: 'Em trânsito',
  PICKUP: 'Disponível para retirada',
  OUT_FOR_DELIVERY: 'Saiu para entrega',
  DELIVERED: 'Entregue',
  FAILED: 'Entrega não realizada',
  EXCEPTION: 'Problema na entrega',
  EXPIRED: 'Rastreio sem atualização',
}

/** Rótulo da etapa 2 muda quando é para retirar. */
export function stageLabel(delivery: Delivery, index: number) {
  if (index === 2 && delivery.status === 'PICKUP') return 'Para retirar'
  return STAGES[index].label
}

/** Espelha `DeliveryCarrier` (código do 17TRACK). */
export const CARRIERS: { key: number; label: string }[] = [
  { key: 2151, label: 'Correios' },
  { key: 101332, label: 'Shopee (SPX Express)' },
  { key: 100457, label: 'Loggi' },
  { key: 100797, label: 'J&T Express' },
  { key: 101052, label: 'Jadlog' },
  { key: 101415, label: 'Total Express' },
  { key: 100887, label: 'Kangu' },
  { key: 100727, label: 'Americanas Entrega' },
  { key: 191915, label: 'Anjun Express' },
  { key: 190271, label: 'Cainiao' },
  { key: 190625, label: 'AliExpress' },
]

/** Etapas que a pessoa lança à mão, na ordem. */
export const MANUAL_STEPS: { status: ManualStatus; label: string }[] = [
  { status: 'POSTED', label: 'Postado' },
  { status: 'IN_TRANSIT', label: 'Em trânsito' },
  { status: 'OUT_FOR_DELIVERY', label: 'Saiu para entrega' },
  { status: 'PICKUP', label: 'Disponível para retirada' },
  { status: 'DELIVERED', label: 'Entregue' },
]

/** A partir de quantos dias sem novidade a entrega aparece como parada (igual ao aviso da API). */
export const STALL_DAYS = 7

/** "12 a 14/11", "14/11" ou nulo. */
export function forecast(delivery: Delivery) {
  const fmt = (d: string) => `${d.slice(8, 10)}/${d.slice(5, 7)}`
  const { estimatedFrom: from, estimatedTo: to } = delivery
  if (!from && !to) return null
  if (from && to && from !== to) return `${fmt(from).slice(0, 2)} a ${fmt(to)}`
  return fmt((to ?? from)!)
}


/** "Rua A, 10 · Apto 2 · Centro · São Paulo/SP". */
export function addressLine(a: DeliveryAddress) {
  const street = [a.street, a.number].filter(Boolean).join(', ')
  return [street, a.complement, a.district, `${a.city}/${a.state}`].filter(Boolean).join(' · ')
}
