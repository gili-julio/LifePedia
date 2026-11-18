import type { UserSummary } from '@/features/groups/types'

/** Espelha `PricePoint.Source`. */
export type PriceSource = 'BUSCAPE' | 'LINK' | 'MANUAL'

export type Offer = {
  store: string
  price: number
  /** Parcelado, quando diferente do à vista */
  totalPrice: number | null
  url: string | null
  source: PriceSource
  day: string
}

export type HistoryPoint = { day: string; price: number; store: string }

/** Últimos 90 dias (menor preço de cada dia); `change7`/`change30` em %. */
export type PriceStats = { min: number | null; avg: number | null; max: number | null; days: number; change7: number | null; change30: number | null }

export type PriceSettings = {
  targetPrice?: number | null
  dropPercent?: number | null
  notifyGroup?: boolean
  updatePlan?: boolean
  active?: boolean
}

/** Espelha `PriceDtos.WatchSummary`. */
export type WatchSummary = {
  id: string
  name: string
  groupId: string | null
  groupName: string | null
  planItemId: string | null
  imageUrl: string | null
  bestPrice: number | null
  bestStore: string | null
  bestUrl: string | null
  storeCount: number | null
  targetPrice: number | null
  dropPercent: number
  notifyGroup: boolean
  updatePlan: boolean
  active: boolean
  change7: number | null
  change30: number | null
  atTarget: boolean
  lastCheckedAt: string | null
  lastError: string | null
  owner: UserSummary | null
  mine: boolean
  canDelete: boolean
  createdAt: string
}

export type LinkView = { id: string; url: string; store: string | null; lastPrice: number | null; lastCheckedAt: string | null; lastError: string | null }

export type ManualView = { id: string; day: string; store: string; price: number; url: string | null }

export type WatchDetail = {
  watch: WatchSummary
  buscapeUrl: string | null
  offers: Offer[]
  history: HistoryPoint[]
  stats: PriceStats
  links: LinkView[]
  manual: ManualView[]
}

export type ManualPriceRequest = { store: string; price: number; day?: string; url?: string }

export type CreateRequest = {
  name: string
  groupId?: string | null
  planItemId?: string | null
  settings?: PriceSettings
  links?: string[]
  price?: ManualPriceRequest
}
