import { EllipsisVerticalIcon, type LucideIcon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { ActionSheet } from '@/components/ActionSheet'
import { HideValuesButton } from '@/components/HideValuesButton'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'

export type PageAction = {
  label: string
  icon: LucideIcon
  /** Ação principal: botão cheio no desktop e atalho visível no mobile. */
  primary?: boolean
  onSelect?: () => void
  to?: string
  /** No desktop fica no menu ⋮ em vez de virar botão. */
  secondary?: boolean
  destructive?: boolean
}

type Props = {
  title: string
  description?: string
  /** Controles sempre visíveis (ex.: seletor de mês). */
  toolbar?: ReactNode
  actions?: PageAction[]
  /** Mostra o olhinho de ocultar valores (telas com dinheiro). */
  hideValues?: boolean
}

/** Cabeçalho de página. No mobile: atalho da ação principal + ⋮ com todas as ações. */
export function PageHeader({ title, description, toolbar, actions = [], hideValues }: Props) {
  const navigate = useNavigate()
  const [sheetOpen, setSheetOpen] = useState(false)
  const primary = actions.find((a) => a.primary)
  const buttons = actions.filter((a) => !a.secondary)
  const menu = actions.filter((a) => a.secondary)
  const run = (action: PageAction) => (action.to ? navigate(action.to) : action.onSelect?.())

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          {description && <p className="text-sm text-muted-foreground">{description}</p>}
        </div>

        {(actions.length > 0 || hideValues) && (
          <div className="flex shrink-0 items-center gap-1 sm:hidden">
            {hideValues && <HideValuesButton size="icon" />}
            {primary && (
              <Button size="icon" aria-label={primary.label} onClick={() => run(primary)}>
                <primary.icon />
              </Button>
            )}
            {actions.length > 1 && (
              <Button variant="ghost" size="icon" aria-label="Mais opções" onClick={() => setSheetOpen(true)}>
                <EllipsisVerticalIcon />
              </Button>
            )}
          </div>
        )}

        <div className="hidden shrink-0 flex-wrap items-center justify-end gap-2 sm:flex">
          {toolbar && <div className="hidden xl:block">{toolbar}</div>}
          {hideValues && <HideValuesButton size="icon" variant="outline" />}
          {buttons.map((action) =>
            action.to ? (
              <Button key={action.label} variant={action.primary ? 'default' : 'outline'} asChild>
                <Link to={action.to}><action.icon /> {action.label}</Link>
              </Button>
            ) : (
              <Button key={action.label} variant={action.primary ? 'default' : 'outline'} onClick={action.onSelect}>
                <action.icon /> {action.label}
              </Button>
            ),
          )}
          {menu.length > 0 && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="icon" aria-label="Mais opções"><EllipsisVerticalIcon /></Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {menu.map((action) => (
                  <DropdownMenuItem key={action.label} variant={action.destructive ? 'destructive' : 'default'} onClick={() => run(action)}>
                    <action.icon /> {action.label}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </div>

      {toolbar && <div className="xl:hidden">{toolbar}</div>}

      {actions.length > 1 && (
        <ActionSheet
          open={sheetOpen}
          onOpenChange={setSheetOpen}
          title={title}
          actions={actions.map((a) => ({ label: a.label, icon: a.icon, destructive: a.destructive, onSelect: () => run(a) }))}
        />
      )}
    </div>
  )
}
