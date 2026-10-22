import { ChevronRightIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useLocation } from 'react-router'
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
import { cn } from '@/lib/utils'
import { isLinkActive, itemLinks, linksByTab, NAV_SECTIONS, type NavItem, type NavLink } from './nav-items'
import { useDynamicLinks } from './use-dynamic-links'
import { UserMenu } from './UserMenu'

/** Navegação lateral: logo no topo, seções com itens que expandem e o usuário no rodapé. */
export function AppSidebar({ unread, onOpenNotifications }: { unread: number; onOpenNotifications: () => void }) {
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
        <UserMenu unread={unread} onOpenNotifications={onOpenNotifications} />
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
                  <span className="truncate">{link.label}</span>
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
                <SidebarMenuSubButton asChild isActive={linkActive(link)}>
                  <Link to={link.to} aria-current={linkActive(link) ? 'page' : undefined}><span>{link.label}</span></Link>
                </SidebarMenuSubButton>
              </SidebarMenuSubItem>
            ))}
          </SidebarMenuSub>
        </CollapsibleContent>
      </SidebarMenuItem>
    </Collapsible>
  )
}
