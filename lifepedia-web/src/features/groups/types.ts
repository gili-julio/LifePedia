/** Papel no grupo (espelha `GroupMember.Role`). */
export type GroupRole = 'ADMIN' | 'MEMBER'

/** Dados públicos de um usuário. */
export type UserSummary = {
  id: string
  name: string
  username: string | null
  /** Nula sem foto; traz `?v=` (versão) */
  imageUrl: string | null
}

export type GroupSummary = {
  id: string
  name: string
  /** Nula sem imagem; traz `?v=` (versão) */
  imageUrl: string | null
  memberCount: number
  /** Papel do usuário atual (nulo em convites recebidos) */
  role: GroupRole | null
}

export type GroupMember = { user: UserSummary; role: GroupRole; joinedAt: string }

export type GroupDetail = {
  id: string
  name: string
  imageUrl: string | null
  role: GroupRole
  members: GroupMember[]
}

/** `status`: já é membro, já foi convidado ou nulo (pode convidar). */
export type InviteCandidate = UserSummary & { status: 'MEMBER' | 'INVITED' | null }

export type SentInvite = { id: string; user: UserSummary; invitedBy: UserSummary; createdAt: string }

export type ReceivedInvite = { id: string; group: GroupSummary; invitedBy: UserSummary; createdAt: string }

export type InviteLink = { token: string; expiresAt: string }

export type LinkPreview = { groupId: string; name: string; imageUrl: string | null; memberCount: number; member: boolean }
