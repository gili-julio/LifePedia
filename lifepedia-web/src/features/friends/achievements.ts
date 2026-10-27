import { FlameIcon, HandshakeIcon, HeartHandshakeIcon, ListChecksIcon, type LucideIcon, ReceiptTextIcon, UsersIcon } from 'lucide-react'
import type { AchievementCode } from './types'

type Info = {
  title: string
  icon: LucideIcon
  /** Texto da meta/valor (ex.: "10 tarefas concluídas"). */
  goal: (n: number) => string
}

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

/** Texto e ícone de cada conquista (espelha `Achievement` da API). */
export const ACHIEVEMENTS: Record<AchievementCode, Info> = {
  TASKS_DONE: {
    title: 'Mãos à obra',
    icon: ListChecksIcon,
    goal: (n) => plural(n, 'tarefa concluída', 'tarefas concluídas'),
  },
  GROUP_TASKS_DONE: {
    title: 'Trabalho em equipe',
    icon: HandshakeIcon,
    goal: (n) => plural(n, 'tarefa de grupo concluída', 'tarefas de grupo concluídas'),
  },
  HABIT_STREAK: {
    title: 'Constância',
    icon: FlameIcon,
    goal: (n) => `${plural(n, 'dia seguido', 'dias seguidos')} num hábito`,
  },
  FRIENDS: {
    title: 'Turma',
    icon: HeartHandshakeIcon,
    goal: (n) => plural(n, 'amigo', 'amigos'),
  },
  GROUPS: {
    title: 'Em grupo',
    icon: UsersIcon,
    goal: (n) => plural(n, 'grupo', 'grupos'),
  },
  TRANSACTIONS: {
    title: 'Organizado',
    icon: ReceiptTextIcon,
    goal: (n) => plural(n, 'lançamento registrado', 'lançamentos registrados'),
  },
}
