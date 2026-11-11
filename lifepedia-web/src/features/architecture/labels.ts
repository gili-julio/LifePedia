import type { ArchRole, Version } from './types'

export const ROLE_LABEL: Record<ArchRole, string> = {
  VIEW: 'Só ver',
  COMMENT: 'Pode comentar',
  EDIT: 'Pode editar',
  OWNER: 'Dono',
}

export const ROLE_HINT: Record<Exclude<ArchRole, 'OWNER'>, string> = {
  VIEW: 'Abre a maquete, os anexos e os comentários.',
  COMMENT: 'Também comenta pontos da maquete.',
  EDIT: 'Também sobe versões, anexos, vistas, variantes, cômodos e luzes (usa o seu espaço).',
}

export const SHARE_ROLES: Exclude<ArchRole, 'OWNER'>[] = ['VIEW', 'COMMENT', 'EDIT']

export const LINK_DAYS = [1, 7, 30, 90, 365]

export function versionName(v: Pick<Version, 'number' | 'label'>) {
  return v.label ? `v${v.number} · ${v.label}` : `Versão ${v.number}`
}

export function meters(value: number) {
  return `${value.toLocaleString('pt-BR', { maximumFractionDigits: 2 })} m`
}

export const projectUrl = (id: string) => `/arquitetura/${id}`

export const publicUrl = (token: string) => `${window.location.origin}/maquete/${token}`
