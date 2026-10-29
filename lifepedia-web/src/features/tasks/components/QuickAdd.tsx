import { Loader2Icon, PlusIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import { useSaveTask } from '../api'
import { useBoard } from '../board'

type Props = {
  /** Dia da tarefa criada (`null` = sem data). */
  dueDate: string | null
  listId: string | null
  placeholder: string
}

/** Campo "digite e aperte Enter" para criar tarefa só com o título. */
export function QuickAdd({ dueDate, listId, placeholder }: Props) {
  const save = useSaveTask()
  const { groupId, meId } = useBoard()
  const [title, setTitle] = useState('')

  async function submit() {
    const value = title.trim()
    if (!value) return
    try {
      await save.mutateAsync({
        groupId,
        data: {
          title: value.slice(0, 200), notes: null, priority: 'NONE', dueDate, dueTime: null, durationMinutes: null,
          listId, tagIds: [], habit: false, repeat: null, subtasks: [], assignees: groupId && meId ? [meId] : [],
          allMembers: false, rotation: [], completion: 'ANY', reminderMinutes: null, recurringEntryId: null,
        },
      })
      setTitle('')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível criar.')
    }
  }

  return (
    <form className="flex gap-2" onSubmit={(e) => {
      e.preventDefault()
      void submit()
    }}>
      <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder={placeholder} maxLength={200}
        aria-label={placeholder} autoComplete="off" />
      <Button type="submit" variant="outline" size="icon" aria-label="Adicionar" disabled={!title.trim() || save.isPending}>
        {save.isPending ? <Loader2Icon className="animate-spin" /> : <PlusIcon />}
      </Button>
    </form>
  )
}
