import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

/** Espelha `AdminDtos.ActiveAnnouncement`. */
export type Announcement = { id: string; message: string; level: 'INFO' | 'WARNING'; endsAt: string | null }

/** Avisos no ar (publicados no painel de administração); confere a cada 5 min. */
export function useAnnouncements() {
  return useQuery({
    queryKey: ['announcements'],
    queryFn: () => api<Announcement[]>('/api/announcements'),
    staleTime: 60_000,
    refetchInterval: 5 * 60_000,
  })
}
