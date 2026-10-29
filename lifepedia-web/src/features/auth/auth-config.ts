import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { AuthConfig } from './types'

/** Configuração pública da tela de login (Client ID do Google). */
export function useAuthConfig() {
  return useQuery({
    queryKey: ['auth', 'config'],
    queryFn: () => api<AuthConfig>('/api/auth/config'),
    staleTime: Infinity,
  })
}
