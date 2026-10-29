import { createBrowserRouter, Navigate } from 'react-router'
import { AppLayout } from '@/components/layout/AppLayout'
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
            path: '/perfil',
            lazy: async () => ({ Component: (await import('@/features/auth/ProfilePage')).ProfilePage }),
          },
        ],
      },
    ],
  },
  // Públicas, com ou sem sessão.
  { path: '/privacidade', lazy: async () => ({ Component: (await import('@/features/legal/PrivacyPage')).PrivacyPage }) },
  { path: '/termos', lazy: async () => ({ Component: (await import('@/features/legal/TermsPage')).TermsPage }) },
  { path: '*', element: <Navigate to="/" replace /> },
])
