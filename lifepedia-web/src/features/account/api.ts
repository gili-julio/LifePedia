import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiDownload } from '@/lib/api'
import type { AccountFile, DeletionPreview, ImportMode, ImportResult } from './types'

const keys = {
  deletionPreview: ['account', 'deletion-preview'] as const,
}

export const downloadExport = () => apiDownload('/api/account/export', 'lifepedia.json')

export const downloadSpreadsheet = () => apiDownload('/api/account/export/spreadsheet', 'lifepedia.xlsx')

/** Prévia (dryRun) ou importação de fato; importar invalida tudo (financeiro, grupos...). */
export function useImportAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, mode, dryRun }: { file: AccountFile; mode: ImportMode; dryRun: boolean }) =>
      api<ImportResult>('/api/account/import', {
        method: 'POST',
        // Perfil, grupos e frequência ficam de fora: são só registro no arquivo. Sem tarefas (@1) = não mexe nelas.
        body: {
          mode,
          dryRun,
          data: {
            format: file.format,
            categories: file.categories,
            recurring: file.recurring,
            transactions: file.transactions,
            taskLists: file.taskLists,
            taskTags: file.taskTags,
            tasks: file.tasks,
          },
        },
      }),
    onSuccess: (result) => {
      if (result.applied) void queryClient.invalidateQueries()
    },
  })
}

export function useDeletionPreview(enabled: boolean) {
  return useQuery({
    queryKey: keys.deletionPreview,
    queryFn: () => api<DeletionPreview>('/api/account/deletion-preview'),
    enabled,
    staleTime: 0,
  })
}

export function useDeleteAccount() {
  return useMutation({
    mutationFn: (data: { password?: string; googleCredential?: string; confirmation: string }) =>
      api<void>('/api/account', { method: 'DELETE', body: data }),
  })
}
