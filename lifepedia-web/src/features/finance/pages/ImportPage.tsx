import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangleIcon, Loader2Icon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useDiscardDraft, useImportAssistant, useImportDraft, useRecurringEntries, useTransactionDescriptions } from '../api'
import { DocumentImport } from '../import/DocumentImport'
import { ImportParseError } from '../import/parse'
import { ImportReview } from '../import/ImportReview'
import { ManualImport } from '../import/ManualImport'
import { rowsOfDraft, type ImportLookups } from '../import/rows'
import type { ImportDraft } from '../types'
import { useCategoryLookup } from '../use-category-lookup'

function Notice({ children, action }: { children: React.ReactNode; action?: React.ReactNode }) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0 text-sm">{children}</div>
        {action}
      </CardContent>
    </Card>
  )
}

function Reading({ draft }: { draft: ImportDraft }) {
  return (
    <Notice>
      <p className="flex items-center gap-2 font-medium">
        <Loader2Icon className="size-4 animate-spin" />
        Lendo {draft.fileCount} documento{draft.fileCount === 1 ? '' : 's'}…
      </p>
      <p className="mt-1 text-muted-foreground">Pode sair desta tela ou fechar o app: a leitura continua e avisamos quando a revisão estiver pronta.</p>
    </Notice>
  )
}

function Failed({ draft, message }: { draft: ImportDraft; message: string }) {
  const discard = useDiscardDraft()
  return (
    <Notice action={<Button variant="outline" disabled={discard.isPending} onClick={() => discard.mutate(draft.id)}>Voltar</Button>}>
      <p className="flex items-center gap-2 font-medium text-destructive"><AlertTriangleIcon className="size-4 shrink-0" /> Não foi possível concluir a leitura</p>
      <p className="mt-1 text-muted-foreground">{message}</p>
    </Notice>
  )
}

/** Abre as linhas gravadas uma vez; dali em diante a revisão manda, e o rascunho vai para a API. */
function DraftReview({ draft, lookups }: { draft: ImportDraft; lookups: ImportLookups }) {
  const queryClient = useQueryClient()
  const { data: rows, error } = useQuery({
    queryKey: ['finance', 'import', 'content', draft.id],
    queryFn: () => rowsOfDraft(draft.id, lookups, queryClient),
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
  })
  if (rows) return <ImportReview key={draft.id} draft={draft} initialRows={rows} fixedEntries={lookups.fixedEntries} />
  if (error) return <Failed draft={draft} message={error instanceof ImportParseError ? error.message : 'Não consegui abrir a revisão.'} />
  return <Notice><p className="flex items-center gap-2"><Loader2Icon className="size-4 animate-spin" /> Abrindo a revisão…</p></Notice>
}

export function ImportPage() {
  const { list: categories, isLoading: loadingCategories } = useCategoryLookup()
  const { data: fixedData, isLoading: loadingFixed } = useRecurringEntries()
  const { data: namesData, isLoading: loadingNames } = useTransactionDescriptions()
  const { data: draft, isLoading: loadingDraft } = useImportDraft()
  const { data: assistant, isLoading: loadingAssistant } = useImportAssistant()
  const lookups = useMemo<ImportLookups>(
    () => ({ categories, fixedEntries: fixedData ?? [], knownNames: namesData ?? [] }),
    [categories, fixedData, namesData],
  )
  const loading = loadingCategories || loadingFixed || loadingNames
  const [tab, setTab] = useState('site')

  function content() {
    if (loading || loadingDraft || loadingAssistant) return null
    if (draft?.status === 'RUNNING') return <Reading draft={draft} />
    if (draft?.status === 'FAILED') return <Failed draft={draft} message={draft.error ?? 'Tente de novo.'} />
    if (draft?.status === 'REVIEW') return <DraftReview draft={draft} lookups={lookups} />
    if (!assistant?.enabled) return <ManualImport lookups={lookups} loading={loading} />
    return (
      <Tabs value={tab} onValueChange={setTab}>
        <TabsList>
          <TabsTrigger value="site">Pelo site</TabsTrigger>
          <TabsTrigger value="manual">Com o seu agente</TabsTrigger>
        </TabsList>
        <TabsContent value="site" className="space-y-6"><DocumentImport assistant={assistant} /></TabsContent>
        <TabsContent value="manual" className="space-y-6"><ManualImport lookups={lookups} loading={loading} /></TabsContent>
      </Tabs>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        hideValues
        title="Importar com IA"
        description="Separe extratos e faturas nas suas categorias e importe tudo de uma vez, um mês ou um período inteiro."
      />
      {content()}
    </div>
  )
}
