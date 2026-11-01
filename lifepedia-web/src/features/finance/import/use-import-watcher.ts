import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useImportDraft } from '../api'

/**
 * Acompanha a leitura em qualquer tela do app. Terminou? Recarrega as notificações, e o toast vem de lá
 * (`useReminderToasts`), que é quem mostra o aviso uma vez só.
 */
export function useImportWatcher() {
  const queryClient = useQueryClient()
  const { data: draft } = useImportDraft()
  const reading = useRef(false)
  const running = draft?.status === 'RUNNING'

  useEffect(() => {
    if (reading.current && !running) {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] })
      void queryClient.invalidateQueries({ queryKey: ['finance', 'import', 'assistant'] })
    }
    reading.current = running
  }, [running, queryClient])
}
