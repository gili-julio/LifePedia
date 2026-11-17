import { EyeOffIcon, PackagePlusIcon, PlusIcon, SparklesIcon, ClipboardPasteIcon, CreditCardIcon, CarIcon, type LucideIcon } from 'lucide-react'
import type { AppModule } from '@/features/auth/types'
import { itemLinks, visibleSections, type DynamicLinks } from '@/components/layout/nav-items'
import { matchesSearch } from '@/lib/search-text'

export type SearchAction = {
  id: string
  label: string
  hint?: string
  icon: LucideIcon
  /** Rota; sem ela, `run`. */
  to?: string
  run?: 'toggle-values'
  keywords?: string
}

type QuickAction = SearchAction & { module?: AppModule }

const QUICK: QuickAction[] = [
  { id: 'new-transaction', label: 'Novo lançamento', icon: PlusIcon, to: '/financeiro/lancamentos?novo=1', module: 'FINANCE', keywords: 'gasto ganho despesa receita' },
  { id: 'quick-purchase', label: 'Compra rápida no cartão', icon: CreditCardIcon, to: '/financeiro/carteira?rapido=1', module: 'FINANCE', keywords: 'fatura carteira' },
  { id: 'paste-purchase', label: 'Colar aviso de compra', icon: ClipboardPasteIcon, to: '/financeiro/carteira?rapido=colar', module: 'FINANCE', keywords: 'notificação banco cartão' },
  { id: 'import', label: 'Importar extrato ou fatura', icon: SparklesIcon, to: '/financeiro/importar', module: 'FINANCE', keywords: 'ia pdf' },
  { id: 'new-delivery', label: 'Nova entrega', icon: PackagePlusIcon, to: '/entregas?nova=1', module: 'DELIVERIES', keywords: 'rastreio código' },
  { id: 'new-vehicle', label: 'Novo veículo', icon: CarIcon, to: '/veiculos?novo=1', module: 'VEHICLES', keywords: 'carro moto' },
  { id: 'toggle-values', label: 'Ocultar ou mostrar valores', icon: EyeOffIcon, run: 'toggle-values', keywords: 'esconder privacidade' },
]

/** Ações rápidas e telas do menu (só dos módulos visíveis) que batem com o texto; sem texto, só as ações. */
export function findActions(query: string, hidden: ReadonlySet<AppModule>, dynamic: DynamicLinks): SearchAction[] {
  const quick = QUICK.filter((a) => !a.module || !hidden.has(a.module))
  if (!query.trim()) return quick.slice(0, 4)
  const pages: SearchAction[] = visibleSections(hidden).flatMap((section) => section.items.flatMap((item) => [
    { id: `page:${item.to}`, label: item.label, hint: 'Tela', icon: item.icon, to: item.to, keywords: section.title },
    ...itemLinks(item, dynamic).filter((link) => link.to !== item.to).map((link) => ({
      id: `page:${link.to}`, label: `${item.label} › ${link.label}`, hint: 'Tela', icon: link.icon ?? item.icon, to: link.to,
    })),
  ]))
  return [...quick, ...pages].filter((a) => matchesSearch(query, a.label, a.keywords)).slice(0, 6)
}
