import { CameraIcon, Trash2Icon } from 'lucide-react'
import { useRef, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { ImageCropDialog, type CropShape } from './ImageCropDialog'
import { cn } from '@/lib/utils'

/** Tipos aceitos e limite do arquivo original (o enviado sai recortado, bem menor). */
const ACCEPT = 'image/jpeg,image/png,image/webp'
export const MAX_INPUT_MB = 10

type Props = {
  /** Prévia atual (avatar) */
  preview: ReactNode
  onChange: (image: Blob) => void
  /** Mostra "Remover" quando há imagem */
  onRemove?: () => void
  shape?: CropShape
}

/** Escolher foto → recortar → devolve o JPEG pronto para enviar. */
export function ImagePicker({ preview, onChange, onRemove, shape = 'round' }: Props) {
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)

  function pick(selected: File | undefined) {
    if (input.current) input.current.value = ''
    if (!selected) return
    if (!ACCEPT.split(',').includes(selected.type)) {
      toast.error('Escolha uma imagem JPEG, PNG ou WebP.')
      return
    }
    if (selected.size > MAX_INPUT_MB * 1024 * 1024) {
      toast.error(`A imagem deve ter até ${MAX_INPUT_MB} MB.`)
      return
    }
    setFile(selected)
  }

  return (
    <div className="flex items-center gap-4">
      <button type="button" onClick={() => input.current?.click()} aria-label="Escolher imagem"
        className={cn('group relative focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none', shape === 'round' ? 'rounded-full' : 'rounded-lg')}>
        {preview}
        <span className={cn('absolute inset-0 flex items-center justify-center bg-black/40 text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100', shape === 'round' ? 'rounded-full' : 'rounded-lg')}>
          <CameraIcon className="size-5" />
        </span>
      </button>
      <div className="flex flex-col items-start gap-1">
        <Button type="button" variant="outline" size="sm" onClick={() => input.current?.click()}>
          <CameraIcon /> Escolher imagem
        </Button>
        {onRemove && (
          <Button type="button" variant="ghost" size="sm" className="text-muted-foreground" onClick={onRemove}>
            <Trash2Icon /> Remover
          </Button>
        )}
        <p className="text-xs text-muted-foreground">JPEG, PNG ou WebP até {MAX_INPUT_MB} MB. Você ajusta o corte antes de salvar.</p>
      </div>
      <input ref={input} type="file" accept={ACCEPT} className="hidden" onChange={(e) => pick(e.target.files?.[0])} />
      <ImageCropDialog
        file={file}
        shape={shape}
        onCancel={() => setFile(null)}
        onConfirm={(image) => {
          setFile(null)
          onChange(image)
        }}
      />
    </div>
  )
}
