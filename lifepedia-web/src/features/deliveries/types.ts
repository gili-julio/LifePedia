import type { UserSummary } from '@/features/groups/types'

/** Espelha `delivery/DeliveryStatus`. */
export type DeliveryStatus =
  | 'PENDING' | 'POSTED' | 'IN_TRANSIT' | 'PICKUP' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'FAILED' | 'EXCEPTION' | 'EXPIRED'

export type DeliveryAddress = {
  id: string
  label: string
  recipient: string | null
  cep: string
  street: string
  number: string | null
  complement: string | null
  district: string | null
  city: string
  state: string
  defaultAddress: boolean
}

export type AddressRequest = Omit<DeliveryAddress, 'id' | 'defaultAddress'> & { defaultAddress?: boolean }

export type TrackEvent = { at: string | null; text: string; location: string | null }

/** Espelha `DeliveryDtos.DeliveryResponse`. */
export type Delivery = {
  id: string
  name: string
  trackingCode: string
  status: DeliveryStatus
  /** 0 postado, 1 em trânsito, 2 saiu para entrega/retirada, 3 entregue; -1 sem informação */
  stage: number
  problem: boolean
  subStatus: string | null
  taxPending: boolean
  carrier: { name: string; url: string | null } | null
  latestAt: string | null
  latestText: string | null
  latestLocation: string | null
  origin: string | null
  estimatedFrom: string | null
  estimatedTo: string | null
  deliveredAt: string | null
  daysInTransit: number | null
  stalledDays: number | null
  arrivesToday: boolean
  imageUrl: string | null
  address: DeliveryAddress | null
  addressShort: { label: string; city: string; state: string } | null
  group: { id: string; name: string } | null
  owner: UserSummary | null
  mine: boolean
  archivedAt: string | null
  lastSyncAt: string | null
  syncError: string | null
  tracking: boolean
  createdAt: string
  events: TrackEvent[] | null
}

export type DeliveryRequest = { trackingCode: string; name: string; addressId: string | null; groupId: string | null }

export type DeliverySummary = { active: number; today: Delivery[]; problems: number; taxes: number }

export type CepResult = { cep: string; street: string; district: string; city: string; state: string }
