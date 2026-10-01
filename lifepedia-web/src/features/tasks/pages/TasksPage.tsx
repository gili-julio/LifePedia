import { PlusIcon, TagsIcon } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { TasksBoard } from '../components/TasksBoard'

/** Tarefas pessoais (e as de grupo em que você é o responsável). */
export function TasksPage() {
  return (
    <TasksBoard group={null} param="aba" header={({ filters, newTask, openLabels }) => (
      <PageHeader title="Tarefas" description="O que fazer hoje, nos próximos dias e sem data."
        toolbar={filters}
        actions={[
          { label: 'Nova tarefa', icon: PlusIcon, primary: true, onSelect: newTask },
          { label: 'Listas e etiquetas', icon: TagsIcon, secondary: true, onSelect: openLabels },
        ]} />
    )} />
  )
}
