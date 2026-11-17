import type { UserSummary } from '@/features/groups/types'

/** Espelha `vehicle/VehicleType`. */
export type VehicleType = 'CAR' | 'MOTORCYCLE' | 'TRUCK' | 'BICYCLE' | 'OTHER'
/** Espelha `vehicle/FipeKind`. */
export type FipeKind = 'CARS' | 'MOTORCYCLES' | 'TRUCKS'
/** Espelha `vehicle/VehicleFuel`. */
export type VehicleFuel = 'GASOLINE' | 'ETHANOL' | 'FLEX' | 'DIESEL' | 'GNV' | 'ELECTRIC' | 'HYBRID'
/** Espelha `vehicle/VehicleRole`. */
export type VehicleRole = 'VIEW' | 'LOG' | 'EDIT' | 'OWNER'
/** Espelha `vehicle/ExpenseKind`. */
export type ExpenseKind = 'FUEL' | 'SERVICE' | 'PART' | 'TAX' | 'LICENSING' | 'INSURANCE' | 'FINE' | 'TOLL' | 'WASH' | 'OTHER'
/** Espelha `vehicle/DocumentKind`. */
export type DocumentKind = 'IPVA' | 'LICENSING' | 'INSURANCE' | 'CNH' | 'INSPECTION' | 'OTHER'
/** Espelha `VehicleMath.Stage`. */
export type Stage = 'OK' | 'SOON' | 'DUE'
export type UpcomingKind = 'ITEM' | 'REVISION' | 'DOCUMENT'

/** Espelha `VehicleMath.Due` (vence no km ou na data, o que vier primeiro). */
export type Due = {
  stage: Stage
  dueKm: number | null
  dueOn: string | null
  kmLeft: number | null
  daysLeft: number | null
  expectedOn: string | null
}

export type Upcoming = { kind: UpcomingKind; id: string; name: string; due: Due }

export type FipeRef = { kind: FipeKind; brand: string; model: string; year: string }

/** Espelha `VehicleDtos.VehicleCard`. */
export type VehicleCard = {
  id: string
  type: VehicleType
  name: string
  brand: string | null
  model: string | null
  modelYear: number | null
  plate: string | null
  color: string | null
  fuel: VehicleFuel | null
  imageUrl: string | null
  odometer: number
  odometerOn: string | null
  estimatedKm: number
  kmPerDay: number | null
  next: Upcoming | null
  alerts: number
  fipeValue: number | null
  owner: UserSummary | null
  mine: boolean
  role: VehicleRole
  sharedVia: string | null
  archivedAt: string | null
}

export type VehicleList = { owned: VehicleCard[]; shared: VehicleCard[] }

export type VehicleItem = {
  id: string
  name: string
  intervalKm: number | null
  intervalMonths: number | null
  lastKm: number | null
  lastOn: string | null
  alert: boolean
  due: Due | null
}

export type VehicleRevision = {
  id: string
  name: string
  dueKm: number | null
  dueOn: string | null
  doneOn: string | null
  doneKm: number | null
  shop: string | null
  notes: string | null
  expenseId: string | null
  due: Due | null
}

export type VehicleDocument = {
  id: string
  kind: DocumentKind
  name: string
  dueOn: string
  amount: number | null
  notes: string | null
  due: Due
}

export type FipeInfo = FipeRef & { code: string | null; value: number | null; month: string | null; checkedAt: string | null }

export type VehicleDetail = {
  vehicle: VehicleCard
  tankLiters: number | null
  notes: string | null
  fipe: FipeInfo | null
  items: VehicleItem[]
  revisions: VehicleRevision[]
  documents: VehicleDocument[]
  upcoming: Upcoming[]
}

export type VehicleRequest = {
  type: VehicleType
  name: string
  brand: string | null
  model: string | null
  modelYear: number | null
  plate: string | null
  color: string | null
  fuel: VehicleFuel | null
  tankLiters: number | null
  odometer?: number | null
  notes: string | null
  fipe: FipeRef | null
  template?: boolean
}

export type Reading = { id: string; km: number; date: string; user: UserSummary | null; fromExpense: boolean; canDelete: boolean }

/** Lançar também no financeiro de quem lançou. */
export type FinanceLink = { categoryId: string; groupIds?: string[] }

export type Expense = {
  id: string
  kind: ExpenseKind
  date: string
  amount: number
  description: string | null
  odometer: number | null
  liters: number | null
  pricePerLiter: number | null
  fuel: VehicleFuel | null
  fullTank: boolean
  station: string | null
  notes: string | null
  imageUrl: string | null
  user: UserSummary | null
  mine: boolean
  canDelete: boolean
  finance: { transactionId: string; categoryId: string } | null
}

export type ExpenseRequest = {
  kind: ExpenseKind
  date: string
  amount: number
  description: string | null
  odometer: number | null
  liters: number | null
  fuel: VehicleFuel | null
  fullTank: boolean
  station: string | null
  notes: string | null
  finance: FinanceLink | null
}

export type ServiceLog = {
  id: string
  name: string
  date: string
  km: number | null
  notes: string | null
  user: UserSummary | null
  expenseId: string | null
  amount: number | null
  itemId: string | null
  revisionId: string | null
}

export type MonthStat = { month: string; fuel: number; maintenance: number; documents: number; other: number; total: number }

export type VehicleStats = {
  months: number
  byMonth: MonthStat[]
  consumption: { date: string; kmPerLiter: number; km: number; liters: number }[]
  total: number
  kmDriven: number | null
  costPerKm: number | null
  avgKmPerLiter: number | null
  lastKmPerLiter: number | null
  avgFuelPrice: number | null
  liters: number | null
}

export type ItemRequest = {
  name: string
  intervalKm: number | null
  intervalMonths: number | null
  lastKm: number | null
  lastOn: string | null
  alert: boolean
}

export type DoneRequest = {
  date: string
  km: number | null
  notes: string | null
  amount: number | null
  shop: string | null
  finance: FinanceLink | null
  itemIds?: string[]
}

export type RevisionRequest = { name: string; dueKm: number | null; dueOn: string | null; notes: string | null }
export type PlanRequest = { everyKm: number | null; everyMonths: number | null; since: string | null; count: number }
export type DocumentRequest = { kind: DocumentKind; name: string; dueOn: string; amount: number | null; notes: string | null }
export type RenewRequest = { nextDue: string; paidOn: string | null; amount: number | null; finance: FinanceLink | null }

export type VehicleSummary = {
  vehicles: number
  upcoming: { vehicleId: string; vehicleName: string; type: VehicleType; upcoming: Upcoming }[]
}

export type GroupRef = { id: string; name: string; imageUrl: string | null }
export type Shares = { users: { user: UserSummary; role: VehicleRole }[]; groups: { group: GroupRef; role: VehicleRole }[] }
export type ShareOptions = { friends: UserSummary[]; groups: GroupRef[] }

export type FipeOption = { code: string; name: string }
export type FipePrice = {
  code: string | null
  value: number | null
  brand: string | null
  model: string | null
  modelYear: number | null
  fuel: string | null
  month: string | null
}
export type Template = { name: string; intervalKm: number | null; intervalMonths: number | null }
