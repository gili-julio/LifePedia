import { Loader2Icon, RotateCwIcon, ZoomInIcon, ZoomOutIcon } from 'lucide-react'
import { useEffect, useState } from 'react'
import Cropper from 'react-easy-crop'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Slider } from '@/components/ui/slider'
import { cropImage, type PixelArea } from './crop-image'

type Props = {
  /** Arquivo escolhido; nulo = fechado */
  file: File | null
  onCancel: () => void
  onConfirm: (image: Blob) => void
  title?: string
  /** Recorte redondo (avatar) ou quadrado (foto de produto) */
  shape?: CropShape
}

export type CropShape = 'round' | 'rect'

const MIN_ZOOM = 1
const MAX_ZOOM = 4

/** Ajuste da foto antes de salvar (como no WhatsApp): arrastar, zoom e girar, recorte redondo ou quadrado. */
export function ImageCropDialog({ file, onCancel, onConfirm, title = 'Ajustar imagem', shape = 'round' }: Props) {
  return (
    <Dialog open={!!file} onOpenChange={(open) => !open && onCancel()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>Arraste para posicionar e use o zoom para cortar.</DialogDescription>
        </DialogHeader>
        {file && <CropEditor file={file} shape={shape} onCancel={onCancel} onConfirm={onConfirm} />}
      </DialogContent>
    </Dialog>
  )
}

/** Editor para um arquivo (recriado a cada arquivo novo). */
function CropEditor({ file, shape, onCancel, onConfirm }: {
  file: File
  shape: CropShape
  onCancel: () => void
  onConfirm: (image: Blob) => void
}) {
  const [src] = useState(() => URL.createObjectURL(file))
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [area, setArea] = useState<PixelArea | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => () => URL.revokeObjectURL(src), [src])

  async function confirm() {
    if (!area) return
    setSaving(true)
    try {
      onConfirm(await cropImage(src, area, rotation))
    } catch {
      toast.error('Não foi possível processar a imagem.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="relative aspect-square w-full overflow-hidden rounded-lg bg-black">
        <Cropper
          image={src}
          crop={crop}
          zoom={zoom}
          rotation={rotation}
          minZoom={MIN_ZOOM}
          maxZoom={MAX_ZOOM}
          aspect={1}
          cropShape={shape}
          showGrid={false}
          onCropChange={setCrop}
          onZoomChange={setZoom}
          onCropComplete={(_, pixels) => setArea(pixels)}
        />
      </div>
      <div className="flex items-center gap-3">
        <ZoomOutIcon className="size-4 shrink-0 text-muted-foreground" />
        <Slider aria-label="Zoom" min={MIN_ZOOM} max={MAX_ZOOM} step={0.01} value={[zoom]}
          onValueChange={([value]) => setZoom(value)} />
        <ZoomInIcon className="size-4 shrink-0 text-muted-foreground" />
        <Button type="button" variant="outline" size="icon-sm" aria-label="Girar 90°"
          onClick={() => setRotation((r) => (r + 90) % 360)}>
          <RotateCwIcon />
        </Button>
      </div>
      <DialogFooter>
        <Button variant="outline" onClick={onCancel}>Cancelar</Button>
        <Button onClick={confirm} disabled={!area || saving}>
          {saving && <Loader2Icon className="animate-spin" />} Usar imagem
        </Button>
      </DialogFooter>
    </>
  )
}
