import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '@/lib/api'
import { useFetchNote, useSaveNote } from './api'
import type { NoteDraft } from './components/NoteDetailsSheet'
import type { Note, NoteRequest } from './types'

/** `conflict`: outra aba ou pessoa salvou antes (a versão local ficou velha). */
export type SaveState = 'saved' | 'pending' | 'saving' | 'error' | 'conflict'

const DELAY_MS = 1000

export function toDraft(note: Note): NoteDraft {
  return {
    title: note.title, content: note.content, folderId: note.folderId, tagIds: note.tagIds, icon: note.icon,
    color: note.color, taskId: note.task?.id ?? null, commitmentId: note.commitment?.id ?? null,
  }
}

/**
 * Rascunho local da nota, salvo 1 s depois da última mudança com a versão que a pessoa tinha.
 * Ao sair da tela com algo pendente, salva na hora; fechar a aba antes disso pede confirmação.
 */
export function useAutosave(groupId: string | null, note: Note) {
  const { mutateAsync: saveNote } = useSaveNote(groupId)
  const fetchNote = useFetchNote(groupId)
  const [draft, setDraft] = useState<NoteDraft>(() => toDraft(note))
  const [state, setStateValue] = useState<SaveState>('saved')
  const draftRef = useRef(draft)
  const stateRef = useRef<SaveState>('saved')
  const version = useRef(note.version)
  const dirty = useRef(false)
  const inflight = useRef<Promise<void> | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const setState = useCallback((next: SaveState) => {
    stateRef.current = next
    setStateValue(next)
  }, [])

  const send = useCallback(() => saveNote({ id: note.id, data: request(draftRef.current, version.current) }),
    [saveNote, note.id])
  const sendRef = useRef(send)
  useEffect(() => {
    sendRef.current = send
  }, [send])

  const flushRef = useRef<() => Promise<void>>(async () => undefined)
  const schedule = useCallback(() => {
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => void flushRef.current(), DELAY_MS)
  }, [])

  const flush = useCallback(async (): Promise<void> => {
    window.clearTimeout(timer.current)
    while (inflight.current) await inflight.current
    if (!dirty.current || stateRef.current === 'conflict') return
    dirty.current = false
    setState('saving')
    const run = (async () => {
      try {
        const saved = await send()
        version.current = saved.version
        setState(dirty.current ? 'pending' : 'saved')
      } catch (error) {
        dirty.current = true
        setState(error instanceof ApiError && error.status === 409 ? 'conflict' : 'error')
      }
    })()
    inflight.current = run
    await run
    inflight.current = null
    if (dirty.current && stateRef.current === 'pending') schedule()
  }, [send, setState, schedule])
  useEffect(() => {
    flushRef.current = flush
  }, [flush])

  const update = useCallback((patch: Partial<NoteDraft>) => {
    draftRef.current = { ...draftRef.current, ...patch }
    setDraft(draftRef.current)
    dirty.current = true
    if (stateRef.current === 'conflict') return
    setState('pending')
    schedule()
  }, [setState, schedule])

  /** Descarta o rascunho e carrega a versão salva por último. */
  const reload = useCallback(async () => {
    const fresh = await fetchNote(note.id, true)
    version.current = fresh.version
    draftRef.current = toDraft(fresh)
    setDraft(draftRef.current)
    dirty.current = false
    setState('saved')
  }, [fetchNote, note.id, setState])

  /** Grava o rascunho por cima da versão mais nova. */
  const keepMine = useCallback(async () => {
    const fresh = await fetchNote(note.id, true)
    version.current = fresh.version
    dirty.current = true
    setState('pending')
    await flush()
  }, [fetchNote, note.id, flush, setState])

  useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (dirty.current || inflight.current) event.preventDefault()
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [])

  useEffect(() => () => {
    window.clearTimeout(timer.current)
    if (dirty.current && !inflight.current && stateRef.current !== 'conflict') void sendRef.current().catch(() => undefined)
  }, [])

  return { draft, update, state, flush, reload, keepMine }
}

function request(draft: NoteDraft, version: number): NoteRequest {
  return { ...draft, title: draft.title.trim() || 'Sem título', version }
}
