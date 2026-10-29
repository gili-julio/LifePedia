import { ChevronRightIcon, MenuIcon, PanelLeftIcon } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import { SidebarInset, SidebarProvider, useSidebar } from '@/components/ui/sidebar'
import { ProfileGate } from '@/features/auth/ProfileGate'
import { LegalGate } from '@/features/legal/LegalGate'
import { useChatStream } from '@/features/chat/use-chat-stream'
import { useUnreadNotifications } from '@/features/notifications/api'
import { NotificationsSheet } from '@/features/notifications/NotificationsSheet'
import { useDevicePush } from '@/features/notifications/push'
import { useReminderToasts } from '@/features/notifications/use-reminder-toasts'
import { useInstallApp } from '@/lib/pwa'
import { AppSidebar } from './AppSidebar'
import { IosInstallDialog } from './IosInstallDialog'
import { findActiveNav } from './nav-items'
import { useDynamicLinks } from './use-dynamic-links'

/** A sidebar grava se está aberta no cookie `sidebar_state`; recolhida, volta recolhida. */
function sidebarStartsOpen() {
  return !document.cookie.split('; ').includes('sidebar_state=false')
}

export function AppLayout() {
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const [iosStepsOpen, setIosStepsOpen] = useState(false)
  const installer = useInstallApp()
  const onInstall = installer.canPrompt ? installer.install : installer.needsIosSteps ? () => setIosStepsOpen(true) : undefined
  // Fora da sidebar: no celular ela desmonta quando a gaveta fecha.
  const { data: unread = 0, isSuccess } = useUnreadNotifications()
  useReminderToasts(isSuccess ? unread : undefined)
  useChatStream()
  // Religa o push deste aparelho à sessão atual (depois de sair e entrar de novo).
  useDevicePush()

  return (
    <SidebarProvider defaultOpen={sidebarStartsOpen()}>
      <AppSidebar unread={unread} onOpenNotifications={() => setNotificationsOpen(true)} onInstall={onInstall} />
      <SidebarInset className="min-w-0">
        <TopBar unread={unread} />
        <div className="mx-auto w-full max-w-6xl px-4 py-8">
          <Outlet />
        </div>
      </SidebarInset>
      <CloseOnNavigate />
      <NotificationsSheet open={notificationsOpen} onOpenChange={setNotificationsOpen} />
      <IosInstallDialog open={iosStepsOpen} onOpenChange={setIosStepsOpen} />
      <LegalGate />
      <ProfileGate />
    </SidebarProvider>
  )
}

/** Topo fino: botão do menu; no celular a logo, no desktop o caminho da página. */
function TopBar({ unread }: { unread: number }) {
  const { pathname, search } = useLocation()
  const active = findActiveNav(pathname, search, useDynamicLinks())
  const { isMobile, toggleSidebar } = useSidebar()
  const label = unread ? `Abrir ou fechar o menu (${unread} notificações não lidas)` : 'Abrir ou fechar o menu'

  return (
    <header className="sticky top-0 z-30 flex h-12 shrink-0 items-center gap-2 border-b bg-background/90 px-4 backdrop-blur lg:rounded-t-xl">
      <span className="relative -ml-1">
        <Button variant="ghost" size="icon-sm" aria-label={label} title="Menu (Ctrl+B)" onClick={toggleSidebar}>
          {isMobile ? <MenuIcon /> : <PanelLeftIcon />}
        </Button>
        {unread > 0 && <span aria-hidden className="absolute top-1 right-1 size-2 rounded-full bg-destructive ring-2 ring-background lg:hidden" />}
      </span>
      <Link to="/" aria-label="Início" className="lg:hidden"><Logo /></Link>
      {active && (
        <p className="hidden min-w-0 items-center gap-1.5 text-sm text-muted-foreground lg:flex">
          <span className="truncate">{active.item.label}</span>
          {active.link && active.link.label !== active.item.label && (
            <>
              <ChevronRightIcon className="size-3.5 shrink-0" aria-hidden />
              <span className="truncate font-medium text-foreground">{active.link.label}</span>
            </>
          )}
        </p>
      )}
    </header>
  )
}

/** Fecha a gaveta do celular ao trocar de página. */
function CloseOnNavigate() {
  const { pathname, search } = useLocation()
  const { setOpenMobile } = useSidebar()
  useEffect(() => setOpenMobile(false), [pathname, search, setOpenMobile])
  return null
}
