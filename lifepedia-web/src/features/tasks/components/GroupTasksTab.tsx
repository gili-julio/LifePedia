import { PlusIcon, TagsIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { GroupDetail } from '@/features/groups/types'
import { TasksBoard } from './TasksBoard'

/** Aba "Tarefas" do grupo: qualquer membro cria, edita e conclui; excluir é de quem criou ou de um admin. */
export function GroupTasksTab({ group }: { group: GroupDetail }) {
  return (
    <TasksBoard group={group} param="vista" header={({ filters, newTask, openLabels }) => (
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">{filters}</div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={openLabels}><TagsIcon /> Listas e etiquetas</Button>
          <Button size="sm" onClick={newTask}><PlusIcon /> Nova tarefa</Button>
        </div>
      </div>
    )} />
  )
}
