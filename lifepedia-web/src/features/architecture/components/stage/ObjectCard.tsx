import { ExternalLinkIcon, PaintbrushIcon, XIcon } from 'lucide-react'
import { Vector3 } from 'three'
import { Button } from '@/components/ui/button'
import { CATEGORY_LABELS } from '../../viewer/categories'
import type { MaterialInfo } from '../../viewer/materials'
import type { ModelObject } from '../../viewer/optimize'

type Props = {
  object: ModelObject
  material: MaterialInfo | null
  onFinish: (index: number) => void
  onClose: () => void
}

/** Ficha da peça tocada: nome, tipo, medidas da caixa, material e o link que veio no nome (se houver). */
export function ObjectCard({ object, material, onFinish, onClose }: Props) {
  const size = object.box.getSize(new Vector3())
  return (
    <section aria-label="Detalhes da peça" className="absolute bottom-2 left-2 z-20 w-[min(20rem,calc(100%-1rem))] space-y-2 rounded-2xl
      border bg-background/95 p-3 text-sm shadow-lg backdrop-blur">
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold">{object.name || CATEGORY_LABELS[object.category]}</p>
          <p className="text-xs text-muted-foreground">{object.name ? CATEGORY_LABELS[object.category] : 'Peça sem nome no arquivo'}</p>
        </div>
        <Button size="icon-xs" variant="ghost" aria-label="Fechar" onClick={onClose}><XIcon /></Button>
      </div>
      <dl className="grid grid-cols-3 gap-2 text-center">
        {[['Largura', size.x], ['Profund.', size.z], ['Altura', size.y]].map(([label, value]) => (
          <div key={label as string} className="rounded-lg bg-muted/60 px-1 py-1.5">
            <dt className="text-[11px] text-muted-foreground">{label}</dt>
            <dd className="font-semibold tabular-nums">{dimension(value as number)}</dd>
          </div>
        ))}
      </dl>
      {material && (
        <div className="flex items-center gap-2">
          {material.preview ? <img src={material.preview} alt="" className="size-6 shrink-0 rounded-md border object-cover" />
            : <span className="size-6 shrink-0 rounded-md border" style={{ backgroundColor: material.color }} />}
          <span className="min-w-0 flex-1 truncate">{material.name}</span>
          <Button size="xs" variant="outline" onClick={() => onFinish(material.index)}><PaintbrushIcon /> Trocar</Button>
        </div>
      )}
      {object.link && (
        <a href={object.link} target="_blank" rel="noopener noreferrer"
          className="flex items-center gap-1.5 text-xs font-medium text-data underline-offset-2 hover:underline">
          <ExternalLinkIcon className="size-3.5 shrink-0" aria-hidden />
          <span className="truncate">Link no nome da peça: {new URL(object.link).host}</span>
        </a>
      )}
      <p className="text-[11px] text-muted-foreground">Medidas da caixa que envolve a peça, aproximadas.</p>
    </section>
  )
}

function dimension(meters: number) {
  return meters < 1 ? `${Math.round(meters * 100)} cm` : `${meters.toLocaleString('pt-BR', { maximumFractionDigits: 2 })} m`
}
