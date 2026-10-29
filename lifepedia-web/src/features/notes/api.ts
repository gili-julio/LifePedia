import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Page } from '@/components/data-table/list-state'
import { api } from '@/lib/api'
import type {
  LinkOptions,
  Note,
  NoteActivity,
  NoteFilters,
  NoteFolder,
  NoteLabels,
  NoteRef,
  NoteRequest,
  NoteSummary,
  NoteTag,
} from './types'

/** Rotas das notas pessoais (`groupId` nulo) ou de um grupo. */
export const notesBase = (groupId: string | null) => (groupId ? `/api/groups/${groupId}/notes` : '/api/notes')
/** Chaves: `['notes', 'me' | groupId, ...]`. */
const scopeKey = (groupId: string | null) => groupId ?? 'me'

export function useNotes(groupId: string | null, filters: NoteFilters, params: { page: number; size: number; sort: string }) {
  return useQuery({
    queryKey: ['notes', scopeKey(groupId), 'list', filters, params],
    queryFn: () => api<Page<NoteSummary>>(notesBase(groupId), {
      query: {
        status: filters.status,
        q: filters.q || undefined,
        folderId: filters.folderId,
        tagId: filters.tagId,
        taskId: filters.taskId,
        commitmentId: filters.commitmentId,
        ...params,
      },
    }),
    placeholderData: keepPreviousData,
  })
}

/** Títulos das notas em uso, para os links `[[Nome]]`. */
export function useNoteRefs(groupId: string | null) {
  return useQuery({ queryKey: ['notes', scopeKey(groupId), 'all'], queryFn: () => api<NoteRef[]>(`${notesBase(groupId)}/all`) })
}

const noteQuery = (groupId: string | null, id: string) => ({
  queryKey: ['notes', scopeKey(groupId), 'note', id],
  queryFn: () => api<Note>(`${notesBase(groupId)}/${id}`),
})

/** A nota aberta não recarrega sozinha (o rascunho local manda); salvar atualiza o cache. */
export function useNote(groupId: string | null, id: string) {
  return useQuery({ ...noteQuery(groupId, id), staleTime: Infinity, refetchOnWindowFocus: false })
}

/** Busca a nota na hora (ex.: conteúdo do modelo ao criar, versão nova num conflito). */
export function useFetchNote(groupId: string | null) {
  const queryClient = useQueryClient()
  return (id: string, fresh = false) => queryClient.fetchQuery({ ...noteQuery(groupId, id), staleTime: fresh ? 0 : 60_000 })
}

export function useNoteLabels(groupId: string | null) {
  return useQuery({ queryKey: ['notes', scopeKey(groupId), 'labels'], queryFn: () => api<NoteLabels>(`${notesBase(groupId)}/labels`) })
}

export function useLinkOptions(groupId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: ['notes', scopeKey(groupId), 'link-options'],
    queryFn: () => api<LinkOptions>(`${notesBase(groupId)}/link-options`),
    enabled,
  })
}

export function useNoteActivity(groupId: string | null, id: string, enabled: boolean) {
  return useQuery({
    queryKey: ['notes', scopeKey(groupId), 'activity', id],
    queryFn: () => api<NoteActivity[]>(`${notesBase(groupId)}/${id}/activity`),
    enabled,
  })
}

/** Invalida o módulo, menos a nota aberta (o rascunho local não pode ser trocado no meio da edição). */
function useNotesMutation<TVariables, TResult>(groupId: string | null, fn: (variables: TVariables) => Promise<TResult>,
  keepNote = false) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({
      queryKey: ['notes', scopeKey(groupId)],
      predicate: (query) => !keepNote || query.queryKey[2] !== 'note',
    }),
  })
}

export function useCreateNote(groupId: string | null) {
  return useNotesMutation(groupId, (data: NoteRequest) => api<Note>(notesBase(groupId), { method: 'POST', body: data }))
}

/** Salva a nota inteira com a versão que a pessoa tinha (409 se outra pessoa salvou antes). */
export function useSaveNote(groupId: string | null) {
  const queryClient = useQueryClient()
  return useNotesMutation(groupId, async ({ id, data }: { id: string; data: NoteRequest }) => {
    const saved = await api<Note>(`${notesBase(groupId)}/${id}`, { method: 'PUT', body: data })
    queryClient.setQueryData(['notes', scopeKey(groupId), 'note', id], saved)
    return saved
  }, true)
}

export function usePinNote(groupId: string | null) {
  return useNotesMutation(groupId, ({ id, pinned }: { id: string; pinned: boolean }) =>
    api<void>(`${notesBase(groupId)}/${id}/pin`, { method: 'PUT', body: { pinned } }))
}

export function useArchiveNote(groupId: string | null) {
  return useNotesMutation(groupId, ({ id, archived }: { id: string; archived: boolean }) =>
    api<void>(`${notesBase(groupId)}/${id}/archive`, { method: 'PUT', body: { archived } }))
}

/** Manda para a lixeira. */
export function useTrashNote(groupId: string | null) {
  return useNotesMutation(groupId, (id: string) => api<void>(`${notesBase(groupId)}/${id}`, { method: 'DELETE' }))
}

export function useRestoreNote(groupId: string | null) {
  return useNotesMutation(groupId, (id: string) => api<void>(`${notesBase(groupId)}/${id}/restore`, { method: 'POST' }))
}

export function useDeleteNoteForever(groupId: string | null) {
  return useNotesMutation(groupId, (id: string) => api<void>(`${notesBase(groupId)}/${id}/permanent`, { method: 'DELETE' }))
}

export function useSaveFolder(groupId: string | null) {
  return useNotesMutation(groupId, ({ id, name }: { id?: string; name: string }) =>
    api<NoteFolder>(id ? `${notesBase(groupId)}/folders/${id}` : `${notesBase(groupId)}/folders`,
      { method: id ? 'PUT' : 'POST', body: { name } }))
}

export function useDeleteFolder(groupId: string | null) {
  return useNotesMutation(groupId, (id: string) => api<void>(`${notesBase(groupId)}/folders/${id}`, { method: 'DELETE' }))
}

export function useSaveTag(groupId: string | null) {
  return useNotesMutation(groupId, ({ id, name, color }: { id?: string; name: string; color: string }) =>
    api<NoteTag>(id ? `${notesBase(groupId)}/tags/${id}` : `${notesBase(groupId)}/tags`,
      { method: id ? 'PUT' : 'POST', body: { name, color } }))
}

export function useDeleteTag(groupId: string | null) {
  return useNotesMutation(groupId, (id: string) => api<void>(`${notesBase(groupId)}/tags/${id}`, { method: 'DELETE' }))
}

/** Envia a foto (já reduzida) e devolve a URL para o markdown. */
export function uploadNoteImage(groupId: string | null, noteId: string, file: Blob) {
  const form = new FormData()
  form.append('file', file, 'foto.jpg')
  return api<{ url: string }>(`${notesBase(groupId)}/${noteId}/images`, { method: 'POST', body: form })
}
