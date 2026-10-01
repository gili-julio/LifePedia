import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  type UniqueIdentifier,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import { restrictToParentElement, restrictToVerticalAxis } from '@dnd-kit/modifiers'
import { arrayMove, SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy } from '@dnd-kit/sortable'
import type { ReactNode } from 'react'

type Props = {
  ids: string[]
  /** Nome do item, para o leitor de tela */
  label: (id: string) => string
  onReorder: (ids: string[]) => void
  children: ReactNode
}

/** Lista reordenável arrastando pela alça (mouse, toque e teclado). */
export function SortableItems({ ids, label, onReorder, children }: Props) {
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )
  const name = (id: UniqueIdentifier) => label(String(id))
  const position = (id: UniqueIdentifier) => `${ids.indexOf(String(id)) + 1}º de ${ids.length}`

  function onDragEnd({ active, over }: DragEndEvent) {
    if (!over || active.id === over.id) return
    onReorder(arrayMove(ids, ids.indexOf(String(active.id)), ids.indexOf(String(over.id))))
  }

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}
      modifiers={[restrictToVerticalAxis, restrictToParentElement]}
      accessibility={{
        screenReaderInstructions: {
          draggable: 'Para mover, aperte espaço ou enter, use as setas para cima e para baixo e aperte espaço de novo para soltar. Esc cancela.',
        },
        announcements: {
          onDragStart: ({ active }) => `Pegou ${name(active.id)}.`,
          onDragOver: ({ active, over }) => over ? `${name(active.id)} vai para ${position(over.id)}.` : undefined,
          onDragEnd: ({ active, over }) => over ? `${name(active.id)} ficou em ${position(over.id)}.` : `Soltou ${name(active.id)}.`,
          onDragCancel: ({ active }) => `Cancelado. ${name(active.id)} voltou ao lugar.`,
        },
      }}>
      <SortableContext items={ids} strategy={verticalListSortingStrategy}>{children}</SortableContext>
    </DndContext>
  )
}
