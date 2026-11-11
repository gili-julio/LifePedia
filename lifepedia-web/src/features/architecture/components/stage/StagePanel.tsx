import { XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type Props = { title: string; onClose: () => void; children: React.ReactNode; className?: string }

/** Painel por cima da maquete: à direita no computador, embaixo no celular, com rolagem própria. */
export function StagePanel({ title, onClose, children, className }: Props) {
  return (
    <section aria-label={title} className={cn('absolute inset-x-2 bottom-2 z-20 flex max-h-[60%] flex-col overflow-hidden rounded-2xl',
      'border bg-background/95 shadow-lg backdrop-blur sm:inset-x-auto sm:top-14 sm:right-2 sm:max-h-none sm:w-80', className)}>
      <header className="flex items-center gap-2 border-b px-3 py-2">
        <h3 className="min-w-0 flex-1 truncate text-sm font-semibold">{title}</h3>
        <Button size="icon-xs" variant="ghost" aria-label="Fechar" onClick={onClose}><XIcon /></Button>
      </header>
      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-3">{children}</div>
    </section>
  )
}
