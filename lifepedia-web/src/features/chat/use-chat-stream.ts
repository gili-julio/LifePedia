import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { chatKeys, upsertMessage } from './api'
import type { ChatMessage } from './types'

/**
 * Um stream SSE por aba (no `AppLayout`) com as mensagens de todos os grupos: atualiza o chat aberto e as não lidas.
 * O navegador reconecta sozinho; ao reconectar, recarrega o chat (pode ter perdido mensagens no meio).
 */
export function useChatStream() {
  const queryClient = useQueryClient()

  useEffect(() => {
    const source = new EventSource('/api/groups/chat/stream')
    let opened = false
    source.onopen = () => {
      if (opened) queryClient.invalidateQueries({ queryKey: chatKeys.all })
      opened = true
    }
    source.addEventListener('message', (event) => {
      const message = JSON.parse((event as MessageEvent<string>).data) as ChatMessage
      upsertMessage(queryClient, message)
      queryClient.invalidateQueries({ queryKey: chatKeys.unread })
    })
    return () => source.close()
  }, [queryClient])
}
