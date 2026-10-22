import { BellIcon, ChevronsUpDownIcon, LogOutIcon, MonitorIcon, MoonIcon, SunIcon, UserRoundIcon } from 'lucide-react'
import { useTheme } from 'next-themes'
import { useNavigate } from 'react-router'
import { UserAvatar } from '@/components/UserAvatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { SidebarMenu, SidebarMenuButton, SidebarMenuItem, useSidebar } from '@/components/ui/sidebar'
import { useAuth } from '@/features/auth/auth-context'
import { cn } from '@/lib/utils'

/** Contador de não lidas (9+ acima de 9). */
export function CountBadge({ count, className }: { count: number; className?: string }) {
  if (!count) return null
  return (
    <span className={cn('flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-white tabular-nums', className)}>
      {count > 9 ? '9+' : count}
    </span>
  )
}

/** Usuário no rodapé da sidebar: notificações, perfil, tema e sair. */
export function UserMenu({ unread, onOpenNotifications }: { unread: number; onOpenNotifications: () => void }) {
  const { user, logout } = useAuth()
  const { theme, setTheme } = useTheme()
  const { isMobile, setOpenMobile } = useSidebar()
  const navigate = useNavigate()
  if (!user) return null

  function openNotifications() {
    setOpenMobile(false)
    onOpenNotifications()
  }

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton size="lg" className="data-[state=open]:bg-sidebar-accent"
              aria-label={unread ? `Menu do usuário (${unread} notificações não lidas)` : 'Menu do usuário'}>
              <span className="relative shrink-0">
                <UserAvatar user={user} size="sm" />
                <CountBadge count={unread} className="absolute -top-1 -right-1 ring-2 ring-sidebar" />
              </span>
              <span className="grid min-w-0 flex-1 text-left leading-tight">
                <span className="truncate font-semibold">{user.name}</span>
                <span className="truncate text-xs text-muted-foreground">{user.username ? `@${user.username}` : user.email}</span>
              </span>
              <ChevronsUpDownIcon className="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent side={isMobile ? 'top' : 'right'} align="end" className="w-60">
            <DropdownMenuLabel className="font-normal">
              <p className="font-medium">{user.name}</p>
              <p className="truncate text-xs text-muted-foreground">{user.username ? `@${user.username}` : user.email}</p>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={openNotifications}>
              <BellIcon /> Notificações
              <CountBadge count={unread} className="ml-auto" />
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => navigate('/perfil')}>
              <UserRoundIcon /> Meu perfil
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuLabel className="text-xs text-muted-foreground">Tema</DropdownMenuLabel>
            <DropdownMenuRadioGroup value={theme} onValueChange={setTheme}>
              <DropdownMenuRadioItem value="light"><SunIcon /> Claro</DropdownMenuRadioItem>
              <DropdownMenuRadioItem value="dark"><MoonIcon /> Escuro</DropdownMenuRadioItem>
              <DropdownMenuRadioItem value="system"><MonitorIcon /> Sistema</DropdownMenuRadioItem>
            </DropdownMenuRadioGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={logout}>
              <LogOutIcon /> Sair
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
