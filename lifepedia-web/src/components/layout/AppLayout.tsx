import { BellIcon, LogOutIcon, MonitorIcon, MoonIcon, SunIcon, UserRoundIcon } from 'lucide-react'
import { useTheme } from 'next-themes'
import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { Logo } from '@/components/Logo'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
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
import { useAuth } from '@/features/auth/auth-context'
import { ProfileGate } from '@/features/auth/ProfileGate'
import { useUnreadNotifications } from '@/features/notifications/api'
import { NotificationsSheet } from '@/features/notifications/NotificationsSheet'
import { useReminderToasts } from '@/features/notifications/use-reminder-toasts'
import { cn } from '@/lib/utils'
import { MobileNav } from './MobileNav'
import { NAV_SECTIONS } from './nav-items'

function initials(name: string) {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
}

/** Contador de não lidas (9+ acima de 9). */
function CountBadge({ count, className }: { count: number; className?: string }) {
  if (!count) return null
  return (
    <span className={cn('flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-white tabular-nums', className)}>
      {count > 9 ? '9+' : count}
    </span>
  )
}

function UserMenu() {
  const { user, logout } = useAuth()
  const { theme, setTheme } = useTheme()
  const navigate = useNavigate()
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const { data: unread = 0, isSuccess } = useUnreadNotifications()
  useReminderToasts(isSuccess ? unread : undefined)
  if (!user) return null

  return (
    <>
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="relative rounded-full"
          aria-label={unread ? `Menu do usuário (${unread} notificações não lidas)` : 'Menu do usuário'}>
          <Avatar className="size-8">
            <AvatarFallback>{initials(user.name)}</AvatarFallback>
          </Avatar>
          <CountBadge count={unread} className="absolute -top-0.5 -right-0.5 ring-2 ring-background" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel className="font-normal">
          <p className="font-medium">{user.name}</p>
          <p className="truncate text-xs text-muted-foreground">{user.username ? `@${user.username}` : user.email}</p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => setNotificationsOpen(true)}>
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
    <NotificationsSheet open={notificationsOpen} onOpenChange={setNotificationsOpen} />
    </>
  )
}

/** Navegação em linha (a partir de `lg`). */
function DesktopNav() {
  return (
    <nav aria-label="Navegação principal" className="hidden flex-1 items-center gap-1 lg:flex">
      {NAV_SECTIONS.map((section) => (
        <div key={section.title} className="flex items-center gap-1">
          <span className="px-1 text-xs font-medium tracking-wide text-muted-foreground uppercase">{section.title}</span>
          {section.items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'rounded-full px-2.5 py-1.5 text-sm whitespace-nowrap transition-colors xl:px-3',
                  isActive ? 'bg-muted font-medium text-foreground' : 'text-muted-foreground hover:text-foreground',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </div>
      ))}
    </nav>
  )
}

export function AppLayout() {
  return (
    <div className="min-h-svh bg-muted/40">
      <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4 lg:gap-4 xl:gap-6">
          <MobileNav />
          <Link to="/" aria-label="Início">
            <Logo />
          </Link>
          <DesktopNav />
          <div className="ml-auto lg:ml-0">
            <UserMenu />
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
      <ProfileGate />
    </div>
  )
}
