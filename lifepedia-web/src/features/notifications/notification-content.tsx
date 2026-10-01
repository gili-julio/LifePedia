import {
  AlarmClockIcon,
  BellIcon,
  BellRingIcon,
  CircleCheckIcon,
  type LucideIcon,
  MessageCircleIcon,
  RepeatIcon,
  UserMinusIcon,
  UserRoundCheckIcon,
  UsersIcon,
} from 'lucide-react'
import { formatDate } from '@/lib/format'
import type { ReactNode } from 'react'
import type { AppNotification } from './types'

type Content = {
  text: ReactNode
  /** Destino do "Ver" */
  link: string
  /** Ícone quando não há imagem */
  icon: LucideIcon
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
    default:
      return { text: 'Nova notificação', link: '/', icon: BellIcon }
  }
}
