import { FilePlus2Icon, TagsIcon } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { NotesBoard } from '../components/NotesBoard'

/** Notas pessoais; `?tarefa=` ou `?compromisso=` filtram as ligadas (links de outras telas). */
export function NotesPage() {
  const [params, setParams] = useSearchParams()
  const taskId = params.get('tarefa')
  const commitmentId = params.get('compromisso')

  return (
    <NotesBoard groupId={null}
      link={{ taskId, commitmentId, onClear: () => setParams({}, { replace: true }) }}
      header={({ newNote, labels }) => (
        <PageHeader title="Notas" description="Anotações em markdown, com pastas, etiquetas e modelos."
          actions={[
            { label: 'Nova nota', icon: FilePlus2Icon, primary: true, onSelect: newNote },
            { label: 'Pastas e etiquetas', icon: TagsIcon, onSelect: labels },
          ]} />
      )} />
  )
}
