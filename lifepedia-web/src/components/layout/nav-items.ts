import { HouseIcon, ListTodoIcon, UsersIcon, WalletIcon, type LucideIcon } from 'lucide-react'

export type NavLink = {
  /** Caminho, podendo ter `?aba=` (ativo só com essa aba). */
  to: string
  label: string
  /** Ativo só no caminho exato. */
  end?: boolean
}

export type NavItem = NavLink & {
  icon: LucideIcon
  /** Subitens fixos. */
  children?: NavLink[]
  /** Subitens vindos da API (antes dos fixos). */
  dynamic?: 'groups'
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
        children: [
          { to: '/financeiro', label: 'Visão geral', end: true },
          { to: '/financeiro/fixos', label: 'Fixos' },
          { to: '/financeiro/lancamentos', label: 'Lançamentos' },
          { to: '/financeiro/importar', label: 'Importar' },
          { to: '/financeiro/categorias', label: 'Categorias' },
        ],
      },
    ],
  },
  {
    title: 'Organização',
    items: [
      {
        to: '/tarefas',
        label: 'Tarefas',
        icon: ListTodoIcon,
        children: [
          { to: '/tarefas', label: 'Hoje', end: true },
          { to: '/tarefas?aba=semana', label: 'Semana' },
          { to: '/tarefas?aba=proximos', label: 'Próximos' },
          { to: '/tarefas?aba=sem-data', label: 'Sem data' },
          { to: '/tarefas?aba=habitos', label: 'Hábitos' },
          { to: '/tarefas?aba=concluidas', label: 'Concluídas' },
        ],
      },
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
        children: [{ to: '/grupos', label: 'Todos os grupos', end: true }],
      },
    ],
  },
]

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
