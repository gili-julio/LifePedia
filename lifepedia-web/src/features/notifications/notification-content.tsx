import {
  AlarmClockIcon,
  HourglassIcon,
  BellIcon,
  BellRingIcon,
  PackageIcon,
  CarIcon,
  CreditCardIcon,
  WrenchIcon,
  CircleXIcon,
  GitMergeIcon,
  GitPullRequestIcon,
  KeyRoundIcon,
  Building2Icon,
  CircleCheckIcon,
  FileCheckIcon,
  FileWarningIcon,
  HeartHandshakeIcon,
  LandmarkIcon,
  MegaphoneIcon,
  type LucideIcon,
  MessageCircleIcon,
  PlugZapIcon,
  RepeatIcon,
  UserMinusIcon,
  UserPlusIcon,
  UserRoundCheckIcon,
  UsersIcon,
  TagIcon,
} from 'lucide-react'
import { formatDate } from '@/lib/format'
import { Amount } from './Amount'
import type { ReactNode } from 'react'
import type { AppNotification } from './types'

const IMPORT_LINK = '/financeiro/importar'
const INTEGRATIONS_LINK = '/integracoes'
const PROVIDER_LABEL: Record<string, string> = { GOOGLE: 'Google Agenda', GITLAB: 'GitLab', GITHUB: 'GitHub', DUOLINGO: 'Duolingo' }

/** "!31 Tela de login (acme/app) · Acme" */
function devItem(n: AppNotification) {
  const title = n.data.title ?? n.data.branch ?? ''
  return <>{n.data.number ? `!${n.data.number} ` : ''}{strong(title)} ({n.data.repoName}) no {n.data.projectName}</>
}

function devLink(n: AppNotification) {
  return `/dev/${encodeURIComponent(n.data.projectId ?? '')}`
}

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
    case 'INTEGRATION_FAILED':
      return {
        text: <>A conta {strong(n.data.nickname)} ({PROVIDER_LABEL[n.data.provider] ?? n.data.provider}) parou de sincronizar. Conecte de novo.</>,
        link: INTEGRATIONS_LINK,
        icon: PlugZapIcon,
      }
    case 'INTEGRATION_EXPIRING':
      return {
        text: <>O token de {strong(n.data.nickname)} ({PROVIDER_LABEL[n.data.provider] ?? n.data.provider}) vence em {formatDate(n.data.date)}</>,
        link: INTEGRATIONS_LINK,
        icon: KeyRoundIcon,
      }
    case 'DEV_PIPELINE_FAILED':
      return { text: <>Pipeline falhou: {devItem(n)}</>, link: devLink(n), icon: CircleXIcon }
    case 'DEV_MR_APPROVED':
      return { text: <>MR aprovado: {devItem(n)}</>, link: devLink(n), icon: CircleCheckIcon }
    case 'DEV_MR_MERGED':
      return {
        text: <>MR aceito: {devItem(n)}{n.data.lane ? <>. Issue movida para {strong(n.data.lane)}</> : null}</>,
        link: devLink(n),
        icon: GitMergeIcon,
      }
    case 'DEV_MR_COMMENTED':
      return { text: <>{n.data.count} comentário(s) novo(s) em {devItem(n)}</>, link: devLink(n), icon: MessageCircleIcon }
    case 'DEV_REVIEW_REQUESTED':
      return { text: <>{strong(n.data.author)} pediu sua revisão: {devItem(n)}</>, link: devLink(n), icon: GitPullRequestIcon }
    case 'DEV_ISSUE_STUCK':
      return {
        text: <>#{n.data.number} {strong(n.data.title)} está em {strong(n.data.lane)} há {n.data.days} dias ({n.data.repoName}) no {n.data.projectName}</>,
        link: devLink(n),
        icon: HourglassIcon,
      }
    case 'DELIVERY_UPDATE':
      return { text: <>{strong(n.data.name)}: {deliveryText(n.data)}</>, link: `/entregas/${n.data.deliveryId}`, icon: PackageIcon }
    case 'VEHICLE_SHARED':
      return {
        text: <>{strong(n.data.actorName)} compartilhou o veículo {strong(n.data.vehicleName)}
          {n.data.groupName ? <> com o grupo {strong(n.data.groupName)}</> : ' com você'}</>,
        link: `/veiculos/${n.data.vehicleId}`,
        icon: CarIcon,
      }
    case 'VEHICLE_REMINDER':
      return { text: <>{strong(n.data.vehicleName)}: {vehicleText(n.data)}</>, link: `/veiculos/${n.data.vehicleId}`, icon: WrenchIcon }
    case 'PRICE_ALERT':
      return { text: <>{strong(n.data.name)}: {priceText(n.data)}</>, link: `/precos/${n.data.watchId}`, icon: TagIcon }
    case 'CARD_REMINDER':
      return { text: <>{strong(n.data.cardName)}: {cardText(n.data)}</>, link: `/financeiro/carteira/${n.data.cardId}`, icon: CreditCardIcon }
    default:
      return { text: 'Nova notificação', link: '/', icon: BellIcon }
  }
}

/** Espelha `PushTexts.priceText`. */
function priceText(data: Record<string, string>): ReactNode {
  const value = <Amount value={data.amount} />
  const store = data.store ? <> em {data.store}</> : null
  return data.kind === 'TARGET' ? <>chegou no seu preço: {value}{store}</> : <>caiu {data.percent}%: {value}{store}</>
}

/** Espelha `PushTexts.cardText`. */
function cardText(data: Record<string, string>): ReactNode {
  const value = <Amount value={data.amount} />
  const day = data.date ? ` (${formatDate(data.date)})` : ''
  switch (data.kind) {
    case 'CLOSING': return <>a fatura fecha{day} com {value}</>
    case 'DUE': return <>a fatura de {value} vence{day}</>
    case 'BUDGET': return <>a fatura chegou a {data.percent}% da meta: {value}</>
    default: return 'novidade na fatura'
  }
}

/** Espelha `PushTexts.vehicleText`. */
function vehicleText(data: Record<string, string>) {
  const due = data.stage === 'DUE'
  const day = data.date ? ` (${formatDate(data.date)})` : ''
  if (data.kind === 'DOCUMENT') return `${data.name} ${due ? 'venceu' : 'vence'}${day}`
  const km = data.kmLeft && !data.kmLeft.startsWith('-') && data.kmLeft !== '0' ? ` (faltam ${data.kmLeft} km)` : day
  return `${due ? 'passou da hora' : 'está chegando'}: ${data.name}${km}`
}

/** Espelha `PushTexts.deliveryText`. */
function deliveryText(data: Record<string, string>) {
  switch (data.status) {
    case 'OUT_FOR_DELIVERY': return 'saiu para entrega'
    case 'PICKUP': return 'chegou e está disponível para retirada'
    case 'DELIVERED': return 'entregue'
    case 'FAILED': return 'tentativa de entrega sem sucesso'
    case 'EXCEPTION': return `problema na entrega: ${data.text ?? 'confira o rastreio'}`
    case 'EXPIRED': return 'o rastreio parou de atualizar'
    case 'TAX': return 'aguardando pagamento de imposto ou taxa'
    case 'STALLED': return `sem novidades há ${data.days} dias`
    default: return data.text ?? 'novidade no rastreio'
  }
}
