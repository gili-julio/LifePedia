import { CopyIcon, RefreshCwIcon, SparklesIcon } from 'lucide-react'
import { useEffect } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { useDaily } from '../api'

type Props = { open: boolean; onOpenChange: (open: boolean) => void }

/** Resumo do daily pela IA (ontem, hoje, bloqueios), pronto para copiar. */
export function DailyDialog({ open, onOpenChange }: Props) {
  const daily = useDaily()
  const { mutate, reset } = daily

  useEffect(() => {
    if (open) mutate()
    else reset()
  }, [open, mutate, reset])

  async function copy() {
    if (!daily.data) return
    try {
      await navigator.clipboard.writeText(daily.data.text)
      toast.success('Copiado.')
    } catch {
      toast.error('Não deu para copiar. Selecione o texto e copie.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2"><SparklesIcon className="size-4" aria-hidden /> Resumo do daily</DialogTitle>
          <DialogDescription>Montado pela IA com seus commits, MRs, issues e reuniões de hoje. Confira antes de usar.</DialogDescription>
        </DialogHeader>
        <DialogBody>
          {daily.isPending ? (
            <div className="space-y-2" aria-live="polite" aria-busy>
              <Skeleton className="h-4 w-1/3" /><Skeleton className="h-4" /><Skeleton className="h-4 w-5/6" />
              <Skeleton className="mt-4 h-4 w-1/3" /><Skeleton className="h-4" />
            </div>
          ) : daily.isError ? (
            <p className="text-sm text-destructive">{daily.error instanceof ApiError ? daily.error.message : 'Não deu para montar o resumo.'}</p>
          ) : daily.data ? (
            <pre className="rounded-xl bg-muted p-4 font-sans text-sm whitespace-pre-wrap">{daily.data.text}</pre>
          ) : null}
        </DialogBody>
        <DialogFooter>
          <Button variant="outline" disabled={daily.isPending} onClick={() => mutate()}><RefreshCwIcon /> Gerar de novo</Button>
          <Button disabled={!daily.data} onClick={() => void copy()}><CopyIcon /> Copiar</Button>
        </DialogFooter>
      </DialogScrollContent>
    </Dialog>
  )
}
