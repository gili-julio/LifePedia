import {
  Building2Icon,
  ChartSplineIcon,
  CalendarCheck2Icon,
  CalendarClockIcon,
  CalendarDaysIcon,
  CalendarRangeIcon,
  CircleCheckIcon,
  CodeXmlIcon,
  FlameIcon,
  HeartHandshakeIcon,
  HouseIcon,
  InboxIcon,
  LandmarkIcon,
  LayoutDashboardIcon,
  LayoutGridIcon,
  NotebookPenIcon,
  PackageIcon,
  PlugZapIcon,
  ListTodoIcon,
  ReceiptTextIcon,
  WalletCardsIcon,
  RepeatIcon,
  SparklesIcon,
  SunIcon,
  TagsIcon,
  TrophyIcon,
  UserPlusIcon,
  UsersIcon,
  UsersRoundIcon,
  WalletIcon,
  type LucideIcon,
} from 'lucide-react'
import type { AppModule } from '@/features/auth/types'

export type NavLink = {
  /** Caminho, podendo ter `?aba=` (ativo só com essa aba). */
  to: string
  label: string
  /** Ativo só no caminho exato. */
  end?: boolean
  icon?: LucideIcon
  /** Atalho de grupo (subitem vindo da API): foto no lugar do ícone e opção de fixar. */
  /** `unread` = mensagens do chat não lidas (bolinha na foto) */
  group?: { id: string; name: string; imageUrl: string | null; pinned: boolean; unread: number }
}

export type NavItem = Omit<NavLink, 'icon'> & {
  icon: LucideIcon
  /** Subitens fixos. */
  children?: NavLink[]
  /** Subitens vindos da API (antes dos fixos). */
  dynamic?: 'groups'
  /** Módulo que a pessoa pode esconder no perfil (sem ele, sempre aparece) */
  module?: AppModule
}

export type NavSection = {
  /** Sem título: itens soltos no topo. */
  title?: string
  items: NavItem[]
}

/** Navegação da sidebar (desktop e celular); um módulo novo ganha uma seção aqui. */
export const NAV_SECTIONS: NavSection[] = [
  {
    items: [{ to: '/', label: 'Início', icon: HouseIcon, end: true }],
  },
  {
    title: 'Finanças',
    items: [
      {
        to: '/financeiro',
        label: 'Financeiro',
        icon: WalletIcon,
        module: 'FINANCE',
        children: [
          { to: '/financeiro', label: 'Visão geral', end: true, icon: LayoutDashboardIcon },
          { to: '/financeiro/fixos', label: 'Fixos', icon: RepeatIcon },
          { to: '/financeiro/lancamentos', label: 'Lançamentos', icon: ReceiptTextIcon },
          { to: '/financeiro/bancos', label: 'Bancos', icon: WalletCardsIcon },
          { to: '/financeiro/simulador', label: 'Simulador', icon: ChartSplineIcon },
          { to: '/financeiro/importar', label: 'Importar com IA', icon: SparklesIcon },
          { to: '/financeiro/categorias', label: 'Categorias', icon: TagsIcon },
        ],
      },
      { to: '/financiamentos', label: 'Financiamentos', icon: LandmarkIcon, module: 'FINANCING' },
    ],
  },
  {
    title: 'Organização',
    items: [
      {
        to: '/tarefas',
        label: 'Tarefas',
        icon: ListTodoIcon,
        module: 'TASKS',
        children: [
          { to: '/tarefas', label: 'Hoje', end: true, icon: SunIcon },
          { to: '/tarefas?aba=semana', label: 'Semana', icon: CalendarDaysIcon },
          { to: '/tarefas?aba=proximos', label: 'Próximos', icon: CalendarClockIcon },
          { to: '/tarefas?aba=sem-data', label: 'Sem data', icon: InboxIcon },
          { to: '/tarefas?aba=habitos', label: 'Hábitos', icon: FlameIcon },
          { to: '/tarefas?aba=concluidas', label: 'Concluídas', icon: CircleCheckIcon },
        ],
      },
      { to: '/agenda', label: 'Agenda', icon: CalendarRangeIcon, module: 'AGENDA' },
      { to: '/frequencia', label: 'Frequência', icon: CalendarCheck2Icon, module: 'ATTENDANCE' },
      { to: '/notas', label: 'Notas', icon: NotebookPenIcon, module: 'NOTES' },
      { to: '/entregas', label: 'Entregas', icon: PackageIcon, module: 'DELIVERIES' },
    ],
  },
  {
    title: 'Social',
    items: [
      {
        to: '/grupos',
        label: 'Grupos',
        icon: UsersIcon,
        dynamic: 'groups',
        module: 'GROUPS',
        children: [{ to: '/grupos', label: 'Ver todos', end: true, icon: LayoutGridIcon }],
      },
      {
        to: '/amigos',
        label: 'Amigos',
        icon: HeartHandshakeIcon,
        module: 'FRIENDS',
        children: [
          { to: '/amigos', label: 'Meus amigos', icon: UsersRoundIcon },
          { to: '/amigos?aba=pedidos', label: 'Pedidos', icon: UserPlusIcon },
          { to: '/amigos?aba=conquistas', label: 'Conquistas', icon: TrophyIcon },
        ],
      },
    ],
  },
  {
    title: 'Trabalho',
    items: [{ to: '/dev', label: 'Dev', icon: CodeXmlIcon, module: 'DEV' }],
  },
  {
    title: 'Casa',
    items: [{ to: '/arquitetura', label: 'Arquitetura', icon: Building2Icon, module: 'ARCHITECTURE' }],
  },
  {
    title: 'Conexões',
    items: [{ to: '/integracoes', label: 'Integrações', icon: PlugZapIcon }],
  },
]

/** Seções com os itens que a pessoa não escondeu (seção sem item some). */
export function visibleSections(hidden: ReadonlySet<AppModule>): NavSection[] {
  return NAV_SECTIONS.map((section) => ({ ...section, items: section.items.filter((i) => !i.module || !hidden.has(i.module)) }))
    .filter((section) => section.items.length > 0)
}

/**
 * Se o link está ativo na localização atual. Com `byTab` (irmãos que apontam para abas da mesma página), a aba da
 * query também precisa bater; sem `?aba=` vale a aba padrão.
 */
export function isLinkActive(link: NavLink, pathname: string, search: string, byTab = false) {
  const [path, query] = link.to.split('?')
  if (byTab) {
    return pathname === path && new URLSearchParams(search).get('aba') === new URLSearchParams(query).get('aba')
  }
  return link.end ? pathname === path : pathname === path || pathname.startsWith(`${path}/`)
}

export type DynamicLinks = Record<NonNullable<NavItem['dynamic']>, NavLink[]>

/** Subitens do item: os da API primeiro, depois os fixos. */
export function itemLinks(item: NavItem, dynamic: DynamicLinks): NavLink[] {
  return [...(item.dynamic ? dynamic[item.dynamic] : []), ...(item.children ?? [])]
}

/** Se os subitens apontam para abas da mesma página. */
export function linksByTab(links: NavLink[]) {
  return links.some((link) => link.to.includes('?'))
}

/** Item e subitem ativos na localização atual (caminho mostrado no topo). */
export function findActiveNav(pathname: string, search: string, dynamic: DynamicLinks) {
  for (const section of NAV_SECTIONS) {
    for (const item of section.items) {
      if (!isLinkActive(item, pathname, search)) continue
      const links = itemLinks(item, dynamic)
      const byTab = linksByTab(links)
      return { item, link: links.find((link) => isLinkActive(link, pathname, search, byTab)) }
    }
  }
  return null
}
