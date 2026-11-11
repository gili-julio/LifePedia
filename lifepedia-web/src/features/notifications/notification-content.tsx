import {
  AlarmClockIcon,
  BellIcon,
  BellRingIcon,
  Building2Icon,
  CircleCheckIcon,
  FileCheckIcon,
  FileWarningIcon,
  HeartHandshakeIcon,
  LandmarkIcon,
  MegaphoneIcon,
  type LucideIcon,
  MessageCircleIcon,
  RepeatIcon,
  UserMinusIcon,
  UserPlusIcon,
  UserRoundCheckIcon,
  UsersIcon,
} from 'lucide-react'
import { formatDate } from '@/lib/format'
import { Amount } from './Amount'
import type { ReactNode } from 'react'
import type { AppNotification } from './types'

const IMPORT_LINK = '/financeiro/importar'

type Content = {
  text: ReactNode
  /** Destino do "Ver" */
  link: string
  /** Ícone quando não há imagem */
  icon: LucideIcon
}

/** Projeto de arquitetura. */
function projectLink(n: AppNotification) {
  return `/arquitetura/${encodeURIComponent(n.data.projectId ?? '')}`
}

/** Link que abre a tarefa: aba Tarefas do grupo ou Tarefas pessoais. */
function taskLink(n: AppNotification) {
  const id = encodeURIComponent(n.data.taskId ?? '')
  return n.groupId ? `/grupos/${n.groupId}?aba=tarefas&tarefa=${id}` : `/tarefas?tarefa=${id}`
}

/** " (dd/mm/aaaa)" quando a notificação tem dia. */
function onDay(n: AppNotification) {
  return n.data.date ? ` (${formatDate(n.data.date)})` : ''
}

const strong = (text: string | undefined) => <strong className="font-medium">{text}</strong>

/** Página do financiamento: pessoal ou do grupo. */
function financingLink(n: AppNotification) {
  const id = encodeURIComponent(n.data.financingId ?? '')
  return n.groupId ? `/grupos/${n.groupId}/financiamentos/${id}` : `/financiamentos/${id}`
}

/** Texto, link e ícone de cada tipo de notificação. */
export function notificationContent(n: AppNotification): Content {
  switch (n.type) {
    case 'TASK_ASSIGNED':
      return {
        text: <>{strong(n.data.actorName)} te deixou responsável por {strong(n.data.taskTitle)}{onDay(n)} em {strong(n.data.groupName)}</>,
        link: taskLink(n),
        icon: UserRoundCheckIcon,
      }
    case 'TASK_TURN':
      return {
        text: <>É a sua vez: {strong(n.data.taskTitle)}{onDay(n)} em {strong(n.data.groupName)}</>,
        link: taskLink(n),
        icon: RepeatIcon,
      }
    case 'TASK_COMPLETED':
      return {
        text: <>{strong(n.data.actorName)} concluiu {strong(n.data.taskTitle)}{onDay(n)} em {strong(n.data.groupName)}</>,
        link: taskLink(n),
        icon: CircleCheckIcon,
      }
    case 'TASK_COMMENT':
      return {
        text: <>{strong(n.data.actorName)} comentou em {strong(n.data.taskTitle)}: “{n.data.preview}”</>,
        link: taskLink(n),
        icon: MessageCircleIcon,
      }
    case 'TASK_OVERDUE':
      return {
        text: <>{strong(n.data.taskTitle)}{onDay(n)} ficou atrasada em {strong(n.data.groupName)}</>,
        link: taskLink(n),
        icon: AlarmClockIcon,
      }
    case 'TASK_REMINDER':
      return {
        text: (
          <>
            Lembrete: {strong(n.data.taskTitle)} às {n.data.time?.slice(0, 5)}{onDay(n)}
            {n.data.groupName && <> em {strong(n.data.groupName)}</>}
          </>
        ),
        link: taskLink(n),
        icon: BellRingIcon,
      }
    case 'GROUP_INVITE':
      return {
        text: (
          <>
            <strong className="font-medium">{n.data.actorName}</strong> te chamou para participar do grupo{' '}
            <strong className="font-medium">{n.data.groupName}</strong>
          </>
        ),
        link: '/grupos?aba=solicitacoes',
        icon: UsersIcon,
      }
    case 'MEMBER_ACCOUNT_DELETED':
      return {
        text: (
          <>
            <strong className="font-medium">{n.data.actorName}</strong> excluiu a conta e saiu do grupo{' '}
            <strong className="font-medium">{n.data.groupName}</strong>
            {n.data.promoted === 'true' ? '. Agora você é administrador do grupo.' : '.'}
          </>
        ),
        link: n.groupId ? `/grupos/${n.groupId}` : '/grupos',
        icon: UserMinusIcon,
      }
    case 'FRIEND_REQUEST':
      return {
        text: <>{strong(n.data.actorName)} quer ser seu amigo</>,
        link: '/amigos?aba=pedidos',
        icon: UserPlusIcon,
      }
    case 'FRIEND_ACCEPTED':
      return {
        text: <>{strong(n.data.actorName)} aceitou seu pedido de amizade</>,
        link: '/amigos',
        icon: HeartHandshakeIcon,
      }
    case 'FINANCING_DUE':
      return {
        text: <>Parcela de {strong(n.data.financingName)} vence{onDay(n)}: <Amount value={n.data.amount} /></>,
        link: financingLink(n),
        icon: LandmarkIcon,
      }
    case 'FINANCING_COST_DUE':
      return {
        text: <>{strong(n.data.costName)} de {strong(n.data.financingName)} vence{onDay(n)}: <Amount value={n.data.amount} /></>,
        link: financingLink(n),
        icon: LandmarkIcon,
      }
    case 'IMPORT_READY':
      return {
        text: <>Importação concluída: {strong(n.data.items)} item(ns) esperam sua revisão</>,
        link: IMPORT_LINK,
        icon: FileCheckIcon,
      }
    case 'IMPORT_FAILED':
      return { text: 'Não foi possível ler seus documentos', link: IMPORT_LINK, icon: FileWarningIcon }
    case 'SYSTEM_MESSAGE':
      return { text: <>{strong(n.data.title)}: {n.data.body}</>, link: '/', icon: MegaphoneIcon }
    case 'ARCHITECTURE_SHARED':
      return {
        text: <>{strong(n.data.actorName)} compartilhou o projeto {strong(n.data.projectName)}
          {n.data.groupName ? <> com o grupo {strong(n.data.groupName)}</> : ' com você'}</>,
        link: projectLink(n),
        icon: Building2Icon,
      }
    case 'ARCHITECTURE_COMMENT':
      return {
        text: <>{strong(n.data.actorName)} comentou em {strong(n.data.projectName)}: “{n.data.preview}”</>,
        link: projectLink(n),
        icon: MessageCircleIcon,
      }
    default:
      return { text: 'Nova notificação', link: '/', icon: BellIcon }
  }
}
