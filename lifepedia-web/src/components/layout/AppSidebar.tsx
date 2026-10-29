import { ChevronRightIcon, PinIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { toast } from 'sonner'
import { Logo } from '@/components/Logo'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarRail,
  useSidebar,
} from '@/components/ui/sidebar'
import { GroupAvatar } from '@/features/groups/components/GroupAvatar'
import { usePinGroup } from '@/features/groups/api'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { isLinkActive, itemLinks, linksByTab, NAV_SECTIONS, type NavItem, type NavLink } from './nav-items'
import { useDynamicLinks } from './use-dynamic-links'
import { UserMenu } from './UserMenu'

/** Navegação lateral: logo no topo, seções com itens que expandem e o usuário no rodapé. */
export function AppSidebar({ unread, onOpenNotifications, onInstall }: {
  unread: number
  onOpenNotifications: () => void
  /** Ausente = não dá para instalar (já instalado ou navegador sem suporte). */
  onInstall?: () => void
}) {
  const dynamic = useDynamicLinks()
  return (
    <Sidebar collapsible="icon" variant="inset">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild tooltip="Início">
              <Link to="/" aria-label="LifePedia, ir para o início"><Logo className="text-base group-data-[collapsible=icon]:[&>span:last-child]:hidden" /></Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {NAV_SECTIONS.map((section, index) => (
          <SidebarGroup key={section.title ?? index}>
            {section.title && <SidebarGroupLabel>{section.title}</SidebarGroupLabel>}
            <SidebarMenu>
              {section.items.map((item) => (
                <NavEntry key={item.to} item={item} links={itemLinks(item, dynamic)} />
              ))}
            </SidebarMenu>
          </SidebarGroup>
        ))}
      </SidebarContent>
      <SidebarFooter>
        <UserMenu unread={unread} onOpenNotifications={onOpenNotifications} onInstall={onInstall} />
      </SidebarFooter>
      <SidebarRail aria-label="Recolher ou expandir o menu" title="Recolher ou expandir o menu (Ctrl+B)" />
    </Sidebar>
  )
}

function NavEntry({ item, links }: { item: NavItem; links: NavLink[] }) {
  const { pathname, search } = useLocation()
  const { state, isMobile } = useSidebar()
  const active = isLinkActive(item, pathname, search)
  const byTab = linksByTab(links)
  const linkActive = (link: NavLink) => isLinkActive(link, pathname, search, byTab)
  // Abre sozinho ao entrar na seção (ex.: por um link dentro da página); fechar continua livre.
  const [open, setOpen] = useState(active)
  const [wasActive, setWasActive] = useState(active)
  if (active !== wasActive) {
    setWasActive(active)
    if (active) setOpen(true)
  }
  const Icon = item.icon

  if (links.length === 0) {
    return (
      <SidebarMenuItem>
        <SidebarMenuButton asChild isActive={active} tooltip={item.label}>
          <Link to={item.to}><Icon /><span>{item.label}</span></Link>
        </SidebarMenuButton>
      </SidebarMenuItem>
    )
  }

  // Recolhida (só ícones): os subitens abrem num menu ao lado.
  if (state === 'collapsed' && !isMobile) {
    return (
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton isActive={active} tooltip={item.label}><Icon /><span>{item.label}</span></SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent side="right" align="start" className="min-w-48">
            <DropdownMenuLabel>{item.label}</DropdownMenuLabel>
            {links.map((link) => (
              <DropdownMenuItem key={link.to} asChild>
                <Link to={link.to} aria-current={linkActive(link) ? 'page' : undefined}
                  className={cn(linkActive(link) && 'font-semibold')}>
                  <LinkIcon link={link} />
                  <span className="truncate">{link.label}</span>
                  {link.group?.pinned && <PinIcon className="ml-auto size-3 text-muted-foreground" aria-label="Fixado" />}
                </Link>
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    )
  }

  return (
    <Collapsible asChild open={open} onOpenChange={setOpen} className="group/collapsible">
      <SidebarMenuItem>
        <CollapsibleTrigger asChild>
          <SidebarMenuButton tooltip={item.label}>
            <Icon /><span>{item.label}</span>
            <ChevronRightIcon className="ml-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 motion-reduce:transition-none" />
          </SidebarMenuButton>
        </CollapsibleTrigger>
        <CollapsibleContent>
          <SidebarMenuSub>
            {links.map((link) => (
              <SidebarMenuSubItem key={link.to}>
                <SidebarMenuSubButton asChild isActive={linkActive(link)} className={cn(link.group && 'pr-8')}>
                  <Link to={link.to} aria-current={linkActive(link) ? 'page' : undefined}>
                    <LinkIcon link={link} />
                    <span>{link.label}</span>
                  </Link>
                </SidebarMenuSubButton>
                {link.group && <PinButton group={link.group} alwaysVisible={isMobile} />}
              </SidebarMenuSubItem>
            ))}
          </SidebarMenuSub>
        </CollapsibleContent>
      </SidebarMenuItem>
    </Collapsible>
  )
}

function LinkIcon({ link }: { link: NavLink }) {
  if (link.group) return <GroupAvatar name={link.group.name} imageUrl={link.group.imageUrl} size="xs" className="-ml-0.5" />
  const Icon = link.icon
  return Icon ? <Icon /> : null
}

/** Fixa/desafixa o grupo (até 3). Fixado: alfinete cheio sempre à vista; os outros aparecem ao passar o mouse ou focar. */
function PinButton({ group, alwaysVisible }: { group: NonNullable<NavLink['group']>; alwaysVisible: boolean }) {
  const pin = usePinGroup()
  const label = group.pinned ? `Desafixar ${group.name}` : `Fixar ${group.name}`
  async function toggle() {
    try {
      await pin.mutateAsync({ id: group.id, pinned: !group.pinned })
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível fixar o grupo.')
    }
  }
  return (
    <button type="button" onClick={toggle} disabled={pin.isPending} aria-label={label} title={label}
      className={cn('absolute top-1/2 right-1 flex size-6 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground outline-none',
        'transition-opacity hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-sidebar-ring motion-reduce:transition-none',
        'group-data-[collapsible=icon]:hidden [&>svg]:size-3.5',
        group.pinned || alwaysVisible ? 'opacity-100' : 'opacity-0 group-hover/menu-sub-item:opacity-100')}>
      <PinIcon className={cn(group.pinned && 'fill-current')} />
    </button>
  )
}
