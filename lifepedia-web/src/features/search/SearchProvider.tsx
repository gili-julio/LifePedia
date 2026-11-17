import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useMatch } from 'react-router'
import { SearchContext } from './search-context'
import { SearchPalette } from './SearchPalette'

/** Busca geral: Ctrl+K (ou ⌘K) em qualquer lugar e "/" fora de campos de texto. Dentro de um grupo, começa nele. */
export function SearchProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<{ id: number; open: boolean; text: string; groupId?: string }>({ id: 0, open: false, text: '' })
  const match = useMatch('/grupos/:groupId/*')
  const groupId = match?.params.groupId && match.params.groupId !== 'convite' ? match.params.groupId : undefined

  const openSearch = useCallback((text = '') => {
    setSession((s) => (s.open ? s : { id: s.id + 1, open: true, text, groupId }))
  }, [groupId])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        openSearch()
      } else if (event.key === '/' && !event.ctrlKey && !event.metaKey && !event.altKey && !typing(event.target)) {
        event.preventDefault()
        openSearch()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [openSearch])

  const value = useMemo(() => ({ openSearch }), [openSearch])
  return (
    <SearchContext.Provider value={value}>
      {children}
      {session.id > 0 && (
        <SearchPalette key={session.id} open={session.open} initialText={session.text} groupId={session.groupId}
          onOpenChange={(open) => setSession((s) => ({ ...s, open }))} />
      )}
    </SearchContext.Provider>
  )
}

function typing(target: EventTarget | null) {
  return target instanceof HTMLElement
    && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
}
