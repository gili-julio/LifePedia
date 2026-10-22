import { Loader2Icon } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { ImagePicker } from '@/components/image/ImagePicker'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import { skipAutoFocusOnTouch } from '@/lib/utils'
import { useRemoveGroupImage, useSaveGroup, useUploadGroupImage } from '../api'
import type { GroupSummary } from '../types'
import { GroupAvatar } from './GroupAvatar'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Sem grupo = criar */
  group?: Pick<GroupSummary, 'id' | 'name' | 'imageUrl'>
  onSaved?: (group: GroupSummary) => void
}

/** Criar/editar grupo: nome e imagem (recortada antes de enviar). */
export function GroupFormDialog(props: Props) {
  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogContent className="sm:max-w-md" onOpenAutoFocus={skipAutoFocusOnTouch}>
        <GroupForm {...props} />
      </DialogContent>
    </Dialog>
  )
}

/** Montado só com o dialog aberto: o estado começa do grupo atual. */
function GroupForm({ onOpenChange, group, onSaved }: Props) {
  const save = useSaveGroup()
  const upload = useUploadGroupImage()
  const removeImage = useRemoveGroupImage()
  const [name, setName] = useState(group?.name ?? '')
  const [image, setImage] = useState<{ blob: Blob; url: string } | null>(null)
  const [removed, setRemoved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const busy = save.isPending || upload.isPending || removeImage.isPending

  useEffect(() => () => {
    if (image) URL.revokeObjectURL(image.url)
  }, [image])

  const previewUrl = image?.url ?? (removed ? null : group?.imageUrl)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) return setError('Informe o nome do grupo.')
    if (trimmed.length > 60) return setError('Máximo de 60 caracteres.')
    try {
      let saved = await save.mutateAsync({ id: group?.id, name: trimmed })
      if (image) saved = await upload.mutateAsync({ id: saved.id, image: image.blob })
      else if (removed && group?.imageUrl) saved = await removeImage.mutateAsync(saved.id)
      toast.success(group ? 'Grupo atualizado.' : 'Grupo criado.')
      onOpenChange(false)
      onSaved?.(saved)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Não foi possível salvar o grupo.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{group ? 'Editar grupo' : 'Novo grupo'}</DialogTitle>
        <DialogDescription>Família, casal, república… Depois é só convidar as pessoas.</DialogDescription>
      </DialogHeader>
      <form id="group-form" onSubmit={submit} noValidate className="space-y-5">
        <ImagePicker
          preview={<GroupAvatar name={name || '?'} imageUrl={previewUrl} size="xl" />}
          onChange={(blob) => {
            setImage({ blob, url: URL.createObjectURL(blob) })
            setRemoved(false)
          }}
          onRemove={previewUrl ? () => {
            setImage(null)
            setRemoved(true)
          } : undefined}
        />
        <Field data-invalid={!!error}>
          <FieldLabel htmlFor="group-name">Nome</FieldLabel>
          <Input id="group-name" value={name} maxLength={60} placeholder="Ex.: Casa" aria-invalid={!!error}
            onChange={(e) => {
              setName(e.target.value)
              setError(null)
            }} />
          <FieldError errors={error ? [{ message: error }] : []} />
        </Field>
      </form>
      <DialogFooter>
        <Button variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
        <Button type="submit" form="group-form" disabled={busy}>
          {busy && <Loader2Icon className="animate-spin" />} {group ? 'Salvar' : 'Criar grupo'}
        </Button>
      </DialogFooter>
    </>
  )
}
