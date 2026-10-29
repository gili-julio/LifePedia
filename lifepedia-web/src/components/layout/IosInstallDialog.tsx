import { PlusSquareIcon, ShareIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

/** iPhone/iPad não têm convite de instalação: mostra o caminho pelo Compartilhar. */
export function IosInstallDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Instalar o LifePedia</DialogTitle>
          <DialogDescription>O app fica na tela de início e abre em tela cheia, sem a barra do navegador.</DialogDescription>
        </DialogHeader>
        <ol className="space-y-3 text-sm">
          <li className="flex items-center gap-3">
            <Step n={1} />
            <span>Toque em <strong>Compartilhar</strong> <ShareIcon className="inline size-4 align-text-bottom" aria-hidden /> na barra do Safari.</span>
          </li>
          <li className="flex items-center gap-3">
            <Step n={2} />
            <span>Escolha <strong>Adicionar à Tela de Início</strong> <PlusSquareIcon className="inline size-4 align-text-bottom" aria-hidden />.</span>
          </li>
          <li className="flex items-center gap-3">
            <Step n={3} />
            <span>Toque em <strong>Adicionar</strong> e abra pelo ícone novo.</span>
          </li>
        </ol>
        <DialogFooter>
          <Button onClick={() => onOpenChange(false)}>Entendi</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Step({ n }: { n: number }) {
  return (
    <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold tabular-nums" aria-hidden>
      {n}
    </span>
  )
}
