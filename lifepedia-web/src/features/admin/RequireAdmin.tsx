import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router'
import { useAuth } from '@/features/auth/auth-context'
import type { User } from '@/features/auth/types'
import { api } from '@/lib/api'

/**
 * Só admin entra; os outros voltam ao início. Reconfere a conta na API ao abrir e ao voltar para a aba (acesso tirado
 * some na hora). Quem protege de verdade é a API: todas as rotas `/api/admin` dão 403 para quem não é admin.
 */
export function RequireAdmin() {
  const { user, setUser } = useAuth()
  const { data: fresh } = useQuery({
    queryKey: ['admin', 'me'],
    queryFn: () => api<User>('/api/users/me'),
    staleTime: 0,
    refetchOnWindowFocus: true,
  })

  useEffect(() => {
    if (fresh && fresh.admin !== user?.admin) setUser(fresh)
  }, [fresh, user?.admin, setUser])

  if (!user?.admin || fresh?.admin === false) return <Navigate to="/" replace />
  return <Outlet />
}
