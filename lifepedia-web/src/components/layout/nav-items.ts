import { LayoutDashboardIcon, ListTodoIcon, ReceiptTextIcon, RepeatIcon, SparklesIcon, TagsIcon, UsersIcon, type LucideIcon } from 'lucide-react'

export type NavItem = {
  to: string
  label: string
  icon: LucideIcon
  /** Ativo só na rota exata. */
  end?: boolean
}

export type NavSection = {
  title: string
  items: NavItem[]
}

/** Navegação do desktop e do mobile; um módulo novo ganha uma seção aqui. */
export const NAV_SECTIONS: NavSection[] = [
  {
    title: 'Financeiro',
    items: [
      { to: '/financeiro', label: 'Visão geral', icon: LayoutDashboardIcon, end: true },
      { to: '/financeiro/fixos', label: 'Fixos', icon: RepeatIcon },
      { to: '/financeiro/lancamentos', label: 'Lançamentos', icon: ReceiptTextIcon },
      { to: '/financeiro/importar', label: 'Importar', icon: SparklesIcon },
      { to: '/financeiro/categorias', label: 'Categorias', icon: TagsIcon },
    ],
  },
  {
    title: 'Organização',
    items: [{ to: '/tarefas', label: 'Tarefas', icon: ListTodoIcon }],
  },
  {
    title: 'Social',
    items: [{ to: '/grupos', label: 'Grupos', icon: UsersIcon }],
  },
]
