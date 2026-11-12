import { PlusIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { ColorPicker } from '@/components/ColorPicker'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { useIntegrations } from '@/features/integrations/api'
import { ApiError } from '@/lib/api'
import { useSaveDevProject } from '../api'
import { LINK_KIND, LINK_KINDS } from '../labels'
import type { DevLink, DevProject } from '../types'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Sem projeto = criar */
  project?: DevProject
  onSaved?: (project: DevProject) => void
}

const NONE = '__none'
const MAX_LINKS = 12

export function DevProjectDialog(props: Props) {
  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogScrollContent className="sm:max-w-xl">
        {props.open && <DevProjectForm {...props} />}
      </DialogScrollContent>
    </Dialog>
  )
}

/** Nome, cor, atalhos (repositório, board, homologação...) e a conta Google das reuniões do projeto. */
function DevProjectForm({ onOpenChange, project, onSaved }: Props) {
  const save = useSaveDevProject()
  const integrations = useIntegrations()
  const googleAccounts = (integrations.data?.accounts ?? []).filter((a) => a.provider === 'GOOGLE')
  const [name, setName] = useState(project?.name ?? '')
  const [color, setColor] = useState(project?.color ?? '#4a3aa7')
  const [description, setDescription] = useState(project?.description ?? '')
  const [links, setLinks] = useState<DevLink[]>(project?.links ?? [])
  const [calendar, setCalendar] = useState(project?.calendarAccountId ?? NONE)
  const [errors, setErrors] = useState<Record<string, string>>({})

  function updateLink(index: number, patch: Partial<DevLink>) {
    setLinks((list) => list.map((l, i) => (i === index ? { ...l, ...patch } : l)))
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    const found: Record<string, string> = {}
    if (!name.trim()) found.name = 'Informe o nome do projeto.'
    links.forEach((l, i) => {
      if (!l.label.trim()) found[`links[${i}].label`] = 'Dê um nome ao atalho.'
      if (!/^https?:\/\/\S+$/.test(l.url.trim())) found[`links[${i}].url`] = 'Comece com http:// ou https://.'
    })
    setErrors(found)
    if (Object.keys(found).length) return
    try {
      const saved = await save.mutateAsync({ id: project?.id, name: name.trim(), color, description: description.trim() || null,
        links: links.map((l) => ({ ...l, label: l.label.trim(), url: l.url.trim() })), calendarAccountId: calendar === NONE ? null : calendar })
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
        <DialogDescription>Um por trabalho ou cliente (ex.: Sebito, Gertec, Nut). Os repositórios entram depois.</DialogDescription>
      </DialogHeader>
      <DialogBody>
        <form id="dev-project-form" onSubmit={submit} noValidate className="space-y-4">
          <Field data-invalid={!!errors.name}>
            <FieldLabel htmlFor="dev-name">Nome</FieldLabel>
            <Input id="dev-name" value={name} maxLength={60} placeholder="Ex.: Gertec" className="text-base md:text-sm"
              aria-invalid={!!errors.name} onChange={(e) => setName(e.target.value)} />
            <FieldError>{errors.name}</FieldError>
          </Field>
          <Field>
            <FieldLabel>Cor</FieldLabel>
            <ColorPicker value={color} onChange={setColor} />
          </Field>
          <Field>
            <FieldLabel htmlFor="dev-description">Descrição (opcional)</FieldLabel>
            <Textarea id="dev-description" value={description} maxLength={300} rows={2} className="text-base md:text-sm"
              onChange={(e) => setDescription(e.target.value)} />
          </Field>

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Atalhos</legend>
            <p className="text-xs text-muted-foreground">Links que você abre todo dia: board, homologação, produção, documentação.</p>
            {links.map((link, index) => (
              <div key={index} className="grid gap-2 rounded-xl border p-2 sm:grid-cols-[8.5rem_1fr_auto]">
                <Select value={link.kind} onValueChange={(kind) => updateLink(index, { kind: kind as DevLink['kind'] })}>
                  <SelectTrigger aria-label="Tipo do atalho" className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {LINK_KINDS.map((kind) => <SelectItem key={kind} value={kind}>{LINK_KIND[kind].label}</SelectItem>)}
                  </SelectContent>
                </Select>
                <div className="grid gap-2 sm:grid-cols-2">
                  <div>
                    <Input value={link.label} maxLength={40} placeholder="Nome" aria-label="Nome do atalho" className="text-base md:text-sm"
                      aria-invalid={!!errors[`links[${index}].label`]} onChange={(e) => updateLink(index, { label: e.target.value })} />
                    <FieldError>{errors[`links[${index}].label`]}</FieldError>
                  </div>
                  <div>
                    <Input value={link.url} maxLength={500} inputMode="url" placeholder="https://..." aria-label="Endereço do atalho"
                      className="text-base md:text-sm" aria-invalid={!!errors[`links[${index}].url`]}
                      onChange={(e) => updateLink(index, { url: e.target.value })} />
                    <FieldError>{errors[`links[${index}].url`]}</FieldError>
                  </div>
                </div>
                <Button type="button" variant="ghost" size="icon" aria-label="Tirar atalho"
                  onClick={() => setLinks((list) => list.filter((_, i) => i !== index))}>
                  <Trash2Icon />
                </Button>
              </div>
            ))}
            {links.length < MAX_LINKS && (
              <Button type="button" variant="outline" size="sm"
                onClick={() => setLinks((list) => [...list, { kind: list.length ? 'OTHER' : 'BOARD', label: '', url: '' }])}>
                <PlusIcon /> Adicionar atalho
              </Button>
            )}
          </fieldset>

          <Field>
            <FieldLabel htmlFor="dev-calendar">Reuniões de qual conta Google</FieldLabel>
            <Select value={calendar} onValueChange={setCalendar}>
              <SelectTrigger id="dev-calendar" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>Nenhuma</SelectItem>
                {googleAccounts.map((a) => <SelectItem key={a.id} value={a.id}>{a.nickname} ({a.login})</SelectItem>)}
              </SelectContent>
            </Select>
            <FieldDescription>
              {googleAccounts.length === 0
                ? <>Ligue o Google Agenda em <Link to="/integracoes" className="underline underline-offset-2">Integrações</Link> para ver as reuniões aqui.</>
                : 'As reuniões dessa conta aparecem na página do projeto.'}
            </FieldDescription>
          </Field>
        </form>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
        <Button type="submit" form="dev-project-form" disabled={save.isPending}>Salvar</Button>
      </DialogFooter>
    </>
  )
}
