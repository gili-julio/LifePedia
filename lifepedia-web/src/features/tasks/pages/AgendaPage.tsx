import { PlusIcon } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { AgendaBoard } from '../agenda/AgendaBoard'

/** Agenda pessoal em horários (as suas tarefas e as de grupo em que você é responsável). */
export function AgendaPage() {
  return (
    <AgendaBoard group={null} header={(newTask) => (
      <PageHeader title="Agenda" description="Suas tarefas com horário, por dia ou semana."
        actions={[{ label: 'Nova tarefa', icon: PlusIcon, primary: true, onSelect: newTask }]} />
    )} />
  )
}
