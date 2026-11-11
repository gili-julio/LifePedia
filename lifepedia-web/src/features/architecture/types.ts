/** Pessoa (espelha `UserSummary` da API). */
export type UserSummary = { id: string; name: string; username: string | null; imageUrl: string | null }

/** Espelha `ArchRole` da API (do menor para o maior). */
export type ArchRole = 'VIEW' | 'COMMENT' | 'EDIT' | 'OWNER'

export const ROLE_ORDER: ArchRole[] = ['VIEW', 'COMMENT', 'EDIT', 'OWNER']

export function canAct(role: ArchRole, needed: ArchRole) {
  return ROLE_ORDER.indexOf(role) >= ROLE_ORDER.indexOf(needed)
}

/** Ponto em metros: `[x, y, z]`. */
export type Point = [number, number, number]

export type Usage = { usedBytes: number; quotaBytes: number; maxModelBytes: number }

export type Version = {
  id: string
  number: number
  label: string | null
  note: string | null
  sizeBytes: number
  sourceName: string | null
  sourceBytes: number | null
  createdBy: UserSummary | null
  createdAt: string
  modelUrl: string
}

export type ProjectSummary = {
  id: string
  name: string
  description: string | null
  role: ArchRole
  owner: UserSummary
  coverUrl: string | null
  currentVersion: Version | null
  updatedAt: string
  /** Nome do grupo quando o acesso vem só por ele */
  sharedVia: string | null
}

export type ProjectList = { owned: ProjectSummary[]; shared: ProjectSummary[]; usage: Usage }

export type SavedView = { id: string; name: string; position: Point; target: Point }

export type ProjectDetail = {
  id: string
  name: string
  description: string | null
  latitude: number | null
  northDegrees: number
  role: ArchRole
  owner: UserSummary
  coverUrl: string | null
  currentVersionId: string | null
  versions: Version[]
  views: SavedView[]
  attachments: number
  openComments: number
  /** Só para quem pode subir versão */
  usage: Usage | null
  updatedAt: string
}

export type ProjectInput = { name: string; description: string | null; latitude: number | null; northDegrees: number }

export type ModelComment = {
  id: string
  versionId: string
  author: UserSummary | null
  body: string
  point: Point
  resolved: boolean
  createdAt: string
  canResolve: boolean
  canDelete: boolean
}

export type Attachment = {
  id: string
  kind: 'IMAGE' | 'PDF'
  name: string
  sizeBytes: number
  uploadedBy: UserSummary | null
  createdAt: string
  fileUrl: string
  canDelete: boolean
}

export type GroupRef = { id: string; name: string; imageUrl: string | null }

export type Shares = {
  users: { user: UserSummary; role: ArchRole }[]
  groups: { group: GroupRef; role: ArchRole }[]
  links: { id: string; token: string; expiresAt: string; createdAt: string }[]
}

export type ShareOptions = { friends: UserSummary[]; groups: GroupRef[] }

export type PublicProject = {
  name: string
  description: string | null
  ownerName: string | null
  latitude: number | null
  northDegrees: number
  version: { number: number; label: string | null; sizeBytes: number; modelUrl: string }
  views: SavedView[]
  scene: Scene
  expiresAt: string
}

/** Ajuste de um material numa variante (nulo = como no arquivo). `texture` é da biblioteca do app. */
export type MaterialOverride = { color: string | null; texture: string | null; scale: number | null }

/** Índice do material no GLB → ajuste. */
export type MaterialOverrides = Record<string, MaterialOverride>

export type Variant = { id: string; versionId: string; name: string; materials: MaterialOverrides; updatedAt: string }

export type Room = {
  id: string
  versionId: string
  name: string
  point: Point
  ceiling: number | null
  area: number | null
  /** Contorno aproximado: pares `[x, z]` em metros */
  outline: [number, number][]
}

export type RoomInput = Omit<Room, 'id' | 'versionId'>

export type Light = { id: string; versionId: string; point: Point; color: string; intensity: number }

export type Scene = { variants: Variant[]; rooms: Room[]; lights: Light[] }
