import { useCallback, useEffect, useRef, useState } from 'react'
import { saveDraftRows } from '../api'
import type { ImportRow } from './parse'

export type DraftSaveState = 'saved' | 'saving' | 'error'

const DELAY_MS = 1000

/** Grava a prévia na API 1 s depois da última edição e também ao sair da tela. O rascunho nunca fica no navegador. */
export function useDraftAutosave(draftId: string, rows: ImportRow[]) {
  const [state, setState] = useState<DraftSaveState>('saved')
  const latest = useRef(rows)
  const saved = useRef<ImportRow[] | null>(rows)
  const timer = useRef<number | undefined>(undefined)
  const queue = useRef<Promise<void>>(Promise.resolve())

  /** Em fila: uma gravação antiga nunca passa por cima de uma nova. */
  const flush = useCallback(() => {
    window.clearTimeout(timer.current)
    const snapshot = latest.current
    if (snapshot === saved.current) return queue.current
    saved.current = snapshot
    setState('saving')
    queue.current = queue.current.then(async () => {
      try {
        await saveDraftRows(draftId, snapshot)
        if (saved.current === snapshot) setState('saved')
      } catch {
        // Fica pendente: a próxima edição, ou o próximo flush, tenta de novo.
        if (saved.current === snapshot) saved.current = null
        setState('error')
      }
    })
    return queue.current
  }, [draftId])

  useEffect(() => {
    latest.current = rows
    if (rows === saved.current) return
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => void flush(), DELAY_MS)
  }, [rows, flush])

  useEffect(() => {
    const onHide = () => {
      if (document.visibilityState === 'hidden') void flush()
    }
    document.addEventListener('visibilitychange', onHide)
    return () => {
      document.removeEventListener('visibilitychange', onHide)
      void flush()
    }
  }, [flush])

  return { state, flush }
}
