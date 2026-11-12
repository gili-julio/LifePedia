import { createBrowserRouter, Navigate } from 'react-router'
import { AppLayout } from '@/components/layout/AppLayout'
import { RequireAdmin } from '@/features/admin/RequireAdmin'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { GuestOnly, RequireAuth } from '@/features/auth/RouteGuards'

export const router = createBrowserRouter([
  {
    element: <GuestOnly />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/cadastro', element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          {
            index: true,
            lazy: async () => ({ Component: (await import('@/features/home/pages/HomePage')).HomePage }),
          },
          {
            path: '/novidades',
            lazy: async () => ({ Component: (await import('@/features/home/pages/ChangelogPage')).ChangelogPage }),
          },
          {
            path: '/financeiro',
            lazy: async () => ({
              Component: (await import('@/features/finance/pages/FinanceDashboardPage')).FinanceDashboardPage,
            }),
          },
          {
            path: '/financeiro/fixos',
            lazy: async () => ({
              Component: (await import('@/features/finance/pages/RecurringEntriesPage')).RecurringEntriesPage,
            }),
          },
          {
            path: '/financeiro/lancamentos',
            lazy: async () => ({
              Component: (await import('@/features/finance/pages/TransactionsPage')).TransactionsPage,
            }),
          },
          {
            path: '/financeiro/bancos',
            lazy: async () => ({ Component: (await import('@/features/finance/pages/BanksPage')).BanksPage }),
          },
          {
            path: '/financeiro/importar',
            lazy: async () => ({
              Component: (await import('@/features/finance/pages/ImportPage')).ImportPage,
            }),
          },
          {
            path: '/financeiro/categorias',
            lazy: async () => ({
              Component: (await import('@/features/finance/pages/CategoriesPage')).CategoriesPage,
            }),
          },
          {
            path: '/financiamentos',
            lazy: async () => ({ Component: (await import('@/features/financing/pages/FinancingsPage')).FinancingsPage }),
          },
          {
            path: '/financiamentos/simular',
            lazy: async () => ({ Component: (await import('@/features/financing/pages/SimulatorPage')).SimulatorPage }),
          },
          {
            path: '/financiamentos/:financingId',
            lazy: async () => ({ Component: (await import('@/features/financing/pages/FinancingPage')).FinancingPage }),
          },
          {
            path: '/tarefas',
            lazy: async () => ({ Component: (await import('@/features/tasks/pages/TasksPage')).TasksPage }),
          },
          {
            path: '/agenda',
            lazy: async () => ({ Component: (await import('@/features/tasks/pages/AgendaPage')).AgendaPage }),
          },
          {
            path: '/frequencia',
            lazy: async () => ({ Component: (await import('@/features/attendance/pages/AttendancePage')).AttendancePage }),
          },
          {
            path: '/notas',
            lazy: async () => ({ Component: (await import('@/features/notes/pages/NotesPage')).NotesPage }),
          },
          {
            path: '/notas/:noteId',
            lazy: async () => ({ Component: (await import('@/features/notes/pages/NotePage')).NotePage }),
          },
          {
            path: '/grupos',
            lazy: async () => ({ Component: (await import('@/features/groups/pages/GroupsPage')).GroupsPage }),
          },
          {
            path: '/grupos/convite/:token',
            lazy: async () => ({ Component: (await import('@/features/groups/pages/JoinGroupPage')).JoinGroupPage }),
          },
          {
            path: '/grupos/:groupId',
            lazy: async () => ({ Component: (await import('@/features/groups/pages/GroupPage')).GroupPage }),
          },
          {
            path: '/grupos/:groupId/notas/:noteId',
            lazy: async () => ({ Component: (await import('@/features/notes/pages/NotePage')).NotePage }),
          },
          {
            path: '/grupos/:groupId/financiamentos/:financingId',
            lazy: async () => ({ Component: (await import('@/features/financing/pages/FinancingPage')).FinancingPage }),
          },
          {
            path: '/amigos',
            lazy: async () => ({ Component: (await import('@/features/friends/pages/FriendsPage')).FriendsPage }),
          },
          {
            path: '/amigos/:friendId',
            lazy: async () => ({
              Component: (await import('@/features/friends/pages/FriendProfilePage')).FriendProfilePage,
            }),
          },
          {
            path: '/arquitetura',
            lazy: async () => ({ Component: (await import('@/features/architecture/pages/ArchitecturePage')).ArchitecturePage }),
          },
          {
            path: '/arquitetura/:projectId',
            lazy: async () => ({ Component: (await import('@/features/architecture/pages/ProjectPage')).ProjectPage }),
          },
          {
            path: '/dev',
            lazy: async () => ({ Component: (await import('@/features/dev/pages/DevPage')).DevPage }),
          },
          {
            path: '/dev/:projectId',
            lazy: async () => ({ Component: (await import('@/features/dev/pages/DevProjectPage')).DevProjectPage }),
          },
          {
            path: '/integracoes',
            lazy: async () => ({ Component: (await import('@/features/integrations/pages/IntegrationsPage')).IntegrationsPage }),
          },
          {
            path: '/perfil',
            lazy: async () => ({ Component: (await import('@/features/auth/ProfilePage')).ProfilePage }),
          },
          {
            element: <RequireAdmin />,
            children: [
              {
                path: '/admin',
                lazy: async () => ({ Component: (await import('@/features/admin/pages/AdminPage')).AdminPage }),
              },
            ],
          },
        ],
      },
    ],
  },
  // Públicas, com ou sem sessão.
  { path: '/privacidade', lazy: async () => ({ Component: (await import('@/features/legal/PrivacyPage')).PrivacyPage }) },
  {
    path: '/maquete/:token',
    lazy: async () => ({ Component: (await import('@/features/architecture/pages/PublicModelPage')).PublicModelPage }),
  },
  { path: '/termos', lazy: async () => ({ Component: (await import('@/features/legal/TermsPage')).TermsPage }) },
  { path: '*', element: <Navigate to="/" replace /> },
])
