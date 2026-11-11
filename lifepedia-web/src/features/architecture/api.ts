import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiUpload, type ProgressListener } from '@/lib/api'
import type {
  ArchRole,
  Attachment,
  Light,
  MaterialOverrides,
  ModelComment,
  Point,
  ProjectDetail,
  ProjectInput,
  ProjectList,
  PublicProject,
  Room,
  RoomInput,
  SavedView,
  Scene,
  ShareOptions,
  Shares,
  Variant,
  Version,
} from './types'

const BASE = '/api/architecture'

/** Chaves sob 'architecture': toda mutação invalida o módulo (e as notificações). */
const keys = {
  all: ['architecture'] as const,
  list: ['architecture', 'list'] as const,
  detail: (id: string) => ['architecture', 'detail', id] as const,
  comments: (id: string, versionId: string) => ['architecture', 'comments', id, versionId] as const,
  scene: (id: string, versionId: string) => ['architecture', 'scene', id, versionId] as const,
  attachments: (id: string) => ['architecture', 'attachments', id] as const,
  shares: (id: string) => ['architecture', 'shares', id] as const,
  options: (id: string) => ['architecture', 'share-options', id] as const,
  public: (token: string) => ['architecture', 'public', token] as const,
}

function useArchMutation<TVars, TResult = void>(fn: (vars: TVars) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      client.invalidateQueries({ queryKey: keys.all })
      client.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useProjects() {
  return useQuery({ queryKey: keys.list, queryFn: () => api<ProjectList>(`${BASE}/projects`) })
}

export function useProject(id: string) {
  return useQuery({ queryKey: keys.detail(id), queryFn: () => api<ProjectDetail>(`${BASE}/projects/${id}`), enabled: !!id })
}

export function useSaveProject() {
  return useArchMutation(({ id, ...body }: ProjectInput & { id?: string }) =>
    api<ProjectDetail>(id ? `${BASE}/projects/${id}` : `${BASE}/projects`, { method: id ? 'PUT' : 'POST', body }))
}

export function useDeleteProject() {
  return useArchMutation((id: string) => api<void>(`${BASE}/projects/${id}`, { method: 'DELETE' }))
}

export function useUploadCover() {
  return useArchMutation(({ id, image }: { id: string; image: Blob }) => {
    const form = new FormData()
    form.append('file', image, 'capa.jpg')
    return api<{ coverUrl: string }>(`${BASE}/projects/${id}/cover`, { method: 'PUT', body: form })
  })
}

export type NewVersion = {
  projectId: string
  model: Uint8Array
  label: string | null
  note: string | null
  sourceName: string
  sourceBytes: number
  onProgress?: ProgressListener
}

/** Reserva o espaço e envia o GLB cru (com progresso); a versão nova vira a atual. */
export function useUploadVersion() {
  return useArchMutation(async ({ projectId, model, label, note, sourceName, sourceBytes, onProgress }: NewVersion) => {
    const pending = await api<{ id: string; uploadUrl: string }>(`${BASE}/projects/${projectId}/versions`, {
      method: 'POST',
      body: { label, note, sizeBytes: model.byteLength, sourceName, sourceBytes },
    })
    const blob = new Blob([model as Uint8Array<ArrayBuffer>], { type: 'model/gltf-binary' })
    return apiUpload<Version>(pending.uploadUrl, blob, 'PUT', onProgress)
  })
}

export function useUpdateVersion() {
  return useArchMutation(({ id, label, note }: { id: string; label: string | null; note: string | null }) =>
    api<Version>(`${BASE}/versions/${id}`, { method: 'PUT', body: { label, note } }))
}

export function useDeleteVersion() {
  return useArchMutation((id: string) => api<void>(`${BASE}/versions/${id}`, { method: 'DELETE' }))
}

export function useSetCurrentVersion() {
  return useArchMutation(({ projectId, versionId }: { projectId: string; versionId: string }) =>
    api<ProjectDetail>(`${BASE}/projects/${projectId}/current`, { method: 'PUT', body: { versionId } }))
}

export function useComments(projectId: string, versionId: string | null) {
  return useQuery({
    queryKey: keys.comments(projectId, versionId ?? ''),
    queryFn: () => api<ModelComment[]>(`${BASE}/projects/${projectId}/comments`, { query: { versionId } }),
    enabled: !!projectId && !!versionId,
  })
}

export function useAddComment() {
  return useArchMutation(({ projectId, ...body }: { projectId: string; versionId: string; body: string; point: Point }) =>
    api<ModelComment>(`${BASE}/projects/${projectId}/comments`, { method: 'POST', body }))
}

export function useResolveComment() {
  return useArchMutation(({ projectId, id, resolved }: { projectId: string; id: string; resolved: boolean }) =>
    api<ModelComment>(`${BASE}/projects/${projectId}/comments/${id}/resolved`, { method: 'PUT', body: { resolved } }))
}

export function useDeleteComment() {
  return useArchMutation(({ projectId, id }: { projectId: string; id: string }) =>
    api<void>(`${BASE}/projects/${projectId}/comments/${id}`, { method: 'DELETE' }))
}

export function useAddView() {
  return useArchMutation(({ projectId, ...body }: { projectId: string; name: string; position: Point; target: Point }) =>
    api<SavedView>(`${BASE}/projects/${projectId}/views`, { method: 'POST', body }))
}

export function useDeleteView() {
  return useArchMutation(({ projectId, id }: { projectId: string; id: string }) =>
    api<void>(`${BASE}/projects/${projectId}/views/${id}`, { method: 'DELETE' }))
}

export function useScene(projectId: string, versionId: string | null) {
  return useQuery({
    queryKey: keys.scene(projectId, versionId ?? ''),
    queryFn: () => api<Scene>(`${BASE}/projects/${projectId}/scene`, { query: { versionId } }),
    enabled: !!projectId && !!versionId,
  })
}

export function useSaveVariant() {
  return useArchMutation(({ projectId, id, versionId, name, materials }:
    { projectId: string; id?: string; versionId: string; name: string; materials: MaterialOverrides }) =>
    id ? api<Variant>(`${BASE}/projects/${projectId}/variants/${id}`, { method: 'PUT', body: { name, materials } })
      : api<Variant>(`${BASE}/projects/${projectId}/variants`, { method: 'POST', body: { versionId, name, materials } }))
}

export function useDeleteVariant() {
  return useArchMutation(({ projectId, id }: { projectId: string; id: string }) =>
    api<void>(`${BASE}/projects/${projectId}/variants/${id}`, { method: 'DELETE' }))
}

export function useAddRoom() {
  return useArchMutation(({ projectId, ...body }: { projectId: string; versionId: string } & RoomInput) =>
    api<Room>(`${BASE}/projects/${projectId}/rooms`, { method: 'POST', body }))
}

export function useRenameRoom() {
  return useArchMutation(({ projectId, id, name }: { projectId: string; id: string; name: string }) =>
    api<Room>(`${BASE}/projects/${projectId}/rooms/${id}`, { method: 'PUT', body: { name } }))
}

export function useDeleteRoom() {
  return useArchMutation(({ projectId, id }: { projectId: string; id: string }) =>
    api<void>(`${BASE}/projects/${projectId}/rooms/${id}`, { method: 'DELETE' }))
}

export function useAddLight() {
  return useArchMutation(({ projectId, ...body }:
    { projectId: string; versionId: string; point: Point; color: string; intensity: number }) =>
    api<Light>(`${BASE}/projects/${projectId}/lights`, { method: 'POST', body }))
}

export function useDeleteLight() {
  return useArchMutation(({ projectId, id }: { projectId: string; id: string }) =>
    api<void>(`${BASE}/projects/${projectId}/lights/${id}`, { method: 'DELETE' }))
}

export function useAttachments(projectId: string) {
  return useQuery({
    queryKey: keys.attachments(projectId),
    queryFn: () => api<Attachment[]>(`${BASE}/projects/${projectId}/attachments`),
  })
}

export function useAddAttachment() {
  return useArchMutation(({ projectId, file, name }: { projectId: string; file: Blob; name: string }) => {
    const form = new FormData()
    form.append('file', file, name)
    form.append('name', name)
    return api<Attachment>(`${BASE}/projects/${projectId}/attachments`, { method: 'POST', body: form })
  })
}

export function useDeleteAttachment() {
  return useArchMutation(({ projectId, id }: { projectId: string; id: string }) =>
    api<void>(`${BASE}/projects/${projectId}/attachments/${id}`, { method: 'DELETE' }))
}

export function useShares(projectId: string, enabled: boolean) {
  return useQuery({ queryKey: keys.shares(projectId), queryFn: () => api<Shares>(`${BASE}/projects/${projectId}/shares`), enabled })
}

export function useShareOptions(projectId: string, enabled: boolean) {
  return useQuery({
    queryKey: keys.options(projectId),
    queryFn: () => api<ShareOptions>(`${BASE}/projects/${projectId}/share-options`),
    enabled,
  })
}

export type ShareTarget = { projectId: string; kind: 'users' | 'groups'; targetId: string }

export function useShare() {
  return useArchMutation(({ projectId, kind, targetId, role }: ShareTarget & { role: ArchRole }) =>
    api<void>(`${BASE}/projects/${projectId}/shares/${kind}/${targetId}`, { method: 'PUT', body: { role } }))
}

export function useUnshare() {
  return useArchMutation(({ projectId, kind, targetId }: ShareTarget) =>
    api<void>(`${BASE}/projects/${projectId}/shares/${kind}/${targetId}`, { method: 'DELETE' }))
}

export function useLeaveProject() {
  return useArchMutation((projectId: string) => api<void>(`${BASE}/projects/${projectId}/shares/me`, { method: 'DELETE' }))
}

export function useCreateLink() {
  return useArchMutation(({ projectId, days }: { projectId: string; days: number }) =>
    api<Shares['links'][number]>(`${BASE}/projects/${projectId}/links`, { method: 'POST', body: { days } }))
}

export function useDeleteLink() {
  return useArchMutation(({ projectId, id }: { projectId: string; id: string }) =>
    api<void>(`${BASE}/projects/${projectId}/links/${id}`, { method: 'DELETE' }))
}

/** Link público (sem login). */
export function usePublicProject(token: string) {
  return useQuery({
    queryKey: keys.public(token),
    queryFn: () => api<PublicProject>(`/api/public/architecture/${encodeURIComponent(token)}`),
    retry: false,
  })
}
