import { useState } from 'react'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { useSaveProject } from '../api'
import type { ProjectDetail } from '../types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Sem projeto = criar */
  project?: Pick<ProjectDetail, 'id' | 'name' | 'description' | 'latitude' | 'northDegrees'>
  onSaved?: (project: ProjectDetail) => void
}

export function ProjectFormDialog(props: Props) {
  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <ProjectForm {...props} />
      </DialogScrollContent>
    </Dialog>
  )
}

function ProjectForm({ onOpenChange, project, onSaved }: Props) {
  const save = useSaveProject()
  const [name, setName] = useState(project?.name ?? '')
  const [description, setDescription] = useState(project?.description ?? '')
  const [latitude, setLatitude] = useState(project?.latitude != null ? String(project.latitude).replace('.', ',') : '')
  const [north, setNorth] = useState(String(project?.northDegrees ?? 0))
  const [errors, setErrors] = useState<Record<string, string>>({})

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    const found: Record<string, string> = {}
    const lat = latitude.trim() ? Number(latitude.trim().replace(',', '.')) : null
    const northValue = north.trim() ? Number(north.trim()) : 0
    if (!name.trim()) found.name = 'Informe o nome do projeto.'
    if (lat != null && (!Number.isFinite(lat) || lat < -90 || lat > 90)) found.latitude = 'Latitude entre -90 e 90 (ex.: -5,79).'
    if (!Number.isInteger(northValue) || northValue < 0 || northValue > 359) found.northDegrees = 'Graus inteiros de 0 a 359.'
    setErrors(found)
    if (Object.keys(found).length) return
    try {
      const saved = await save.mutateAsync({ id: project?.id, name: name.trim(), description: description.trim() || null,
        latitude: lat, northDegrees: northValue })
      toast.success(project ? 'Projeto atualizado.' : 'Projeto criado.')
      onOpenChange(false)
      onSaved?.(saved)
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) setErrors(err.fieldErrors)
      else toast.error(err instanceof ApiError ? err.message : 'Não foi possível salvar o projeto.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{project ? 'Editar projeto' : 'Novo projeto'}</DialogTitle>
        <DialogDescription>Uma casa, um apartamento ou um ambiente. A maquete vem depois.</DialogDescription>
      </DialogHeader>
      <DialogBody>
        <form id="project-form" onSubmit={submit} noValidate className="space-y-4">
          <Field data-invalid={!!errors.name}>
            <FieldLabel htmlFor="project-name">Nome</FieldLabel>
            <Input id="project-name" value={name} maxLength={120} placeholder="Ex.: Nossa casa" className="text-base md:text-sm"
              aria-invalid={!!errors.name} onChange={(e) => setName(e.target.value)} />
            <FieldError>{errors.name}</FieldError>
          </Field>
          <Field>
            <FieldLabel htmlFor="project-description">Descrição (opcional)</FieldLabel>
            <Textarea id="project-description" value={description} maxLength={1000} rows={3} className="text-base md:text-sm"
              onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field data-invalid={!!errors.latitude}>
              <FieldLabel htmlFor="project-latitude">Latitude (opcional)</FieldLabel>
              <Input id="project-latitude" inputMode="decimal" value={latitude} placeholder="Ex.: -5,79" className="text-base md:text-sm"
                aria-invalid={!!errors.latitude} onChange={(e) => setLatitude(e.target.value)} />
              <FieldDescription>Para o sol e a sombra. Sul é negativo.</FieldDescription>
              <FieldError>{errors.latitude}</FieldError>
            </Field>
            <Field data-invalid={!!errors.northDegrees}>
              <FieldLabel htmlFor="project-north">Norte (graus)</FieldLabel>
              <Input id="project-north" inputMode="numeric" value={north} className="text-base md:text-sm"
                aria-invalid={!!errors.northDegrees} onChange={(e) => setNorth(e.target.value)} />
              <FieldDescription>Quanto o norte gira, no sentido horário, a partir do fundo da maquete.</FieldDescription>
              <FieldError>{errors.northDegrees}</FieldError>
            </Field>
          </div>
        </form>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
        <Button type="submit" form="project-form" disabled={save.isPending}>Salvar</Button>
      </DialogFooter>
    </>
  )
}
