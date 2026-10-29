import { zodResolver } from '@hookform/resolvers/zod'
import { CheckCircle2Icon, DownloadIcon, Loader2Icon, FileSpreadsheetIcon } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { GoogleButton } from '@/features/auth/GoogleButton'
import { ApiError } from '@/lib/api'
import { downloadExport, downloadSpreadsheet, useDeleteAccount, useDeletionPreview } from '../api'
import { count } from '../count'
import type { DeletionPreview, GroupImpact } from '../types'

const schema = z.object({
  password: z.string(),
  confirmation: z.string().refine((v) => v === 'EXCLUIR', 'Digite EXCLUIR, em maiúsculas.'),
})

type FormInput = z.input<typeof schema>
type FormValues = z.output<typeof schema>

type Props = { open: boolean; onOpenChange: (open: boolean) => void }

/** Mostra o que some (e o que acontece em cada grupo), oferece baixar os dados e pede senha (ou Google) + "EXCLUIR". */
export function DeleteAccountDialog({ open, onOpenChange }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Excluir minha conta</DialogTitle>
          <DialogDescription>Não dá para desfazer. Tudo o que é seu no LifePedia é apagado.</DialogDescription>
        </DialogHeader>
        <DeleteForm onCancel={() => onOpenChange(false)} />
      </DialogScrollContent>
    </Dialog>
  )
}

function DeleteForm({ onCancel }: { onCancel: () => void }) {
  const { user, logout } = useAuth()
  const googleOnly = !!user && !user.hasPassword
  const [googleCredential, setGoogleCredential] = useState<string | null>(null)
  const navigate = useNavigate()
  const preview = useDeletionPreview(true)
  const remove = useDeleteAccount()
  const [downloading, setDownloading] = useState<'json' | 'xlsx' | null>(null)
  const form = useForm<FormInput, unknown, FormValues>({ resolver: zodResolver(schema), defaultValues: { password: '', confirmation: '' } })
  const { errors } = form.formState

  async function download(kind: 'json' | 'xlsx') {
    setDownloading(kind)
    try {
      await (kind === 'json' ? downloadExport() : downloadSpreadsheet())
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível baixar.')
    } finally {
      setDownloading(null)
    }
  }

  async function onSubmit(values: FormValues) {
    if (!googleOnly && !values.password) {
      form.setError('password', { message: 'Informe sua senha.' })
      return
    }
    if (googleOnly && !googleCredential) {
      toast.error('Confirme com a sua conta Google.')
      return
    }
    try {
      await remove.mutateAsync(googleOnly
        ? { googleCredential: googleCredential!, confirmation: values.confirmation }
        : { password: values.password, confirmation: values.confirmation })
      toast.success('Conta excluída. Até mais!')
      logout()
      navigate('/login', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 400 && /senha/i.test(error.message)) {
        form.setError('password', { message: error.message })
      } else {
        if (googleOnly) setGoogleCredential(null)
        toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir a conta.')
      }
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="contents">
      <DialogBody>
        {preview.data ? <Impact preview={preview.data} /> : <Skeleton className="h-28 rounded-lg" />}

        <div className="space-y-2 rounded-lg border p-3">
          <p className="text-sm font-medium">Quer guardar uma cópia antes?</p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" size="sm" disabled={!!downloading} onClick={() => download('json')}>
              {downloading === 'json' ? <Loader2Icon className="animate-spin" /> : <DownloadIcon />} Baixar meus dados
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={!!downloading} onClick={() => download('xlsx')}>
              {downloading === 'xlsx' ? <Loader2Icon className="animate-spin" /> : <FileSpreadsheetIcon />} Planilha
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">O .json pode ser importado depois em outra conta.</p>
        </div>

        <FieldGroup className="gap-4">
          {googleOnly ? (
            <Field>
              <FieldLabel>Confirme que é você</FieldLabel>
              {googleCredential ? (
                <p className="flex items-center gap-2 text-sm"><CheckCircle2Icon className="size-4 text-income" /> Confirmado com o Google.</p>
              ) : (
                <GoogleButton onCredential={setGoogleCredential} text="continue_with" />
              )}
              <FieldDescription>Sua conta entra pelo Google, então a confirmação é por ele.</FieldDescription>
            </Field>
          ) : (
            <Field data-invalid={!!errors.password}>
              <FieldLabel htmlFor="delete-password">Sua senha</FieldLabel>
              <Input id="delete-password" type="password" autoComplete="current-password" aria-invalid={!!errors.password}
                {...form.register('password')} />
              <FieldError errors={[errors.password]} />
            </Field>
          )}
          <Field data-invalid={!!errors.confirmation}>
            <FieldLabel htmlFor="delete-confirmation">Digite EXCLUIR para confirmar</FieldLabel>
            <Input id="delete-confirmation" autoComplete="off" autoCapitalize="characters" spellCheck={false}
              aria-invalid={!!errors.confirmation} {...form.register('confirmation')} />
            {errors.confirmation ? <FieldError errors={[errors.confirmation]} />
              : <FieldDescription>Em maiúsculas.</FieldDescription>}
          </Field>
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel}>Cancelar</Button>
        <Button type="submit" variant="destructive" disabled={remove.isPending || !preview.data}>
          {remove.isPending && <Loader2Icon className="animate-spin" />}
          Excluir minha conta
        </Button>
      </DialogFooter>
    </form>
  )
}

function Impact({ preview }: { preview: DeletionPreview }) {
  return (
    <div className="space-y-2 rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-sm">
      <p className="font-medium">O que vai ser apagado</p>
      <ul className="list-disc space-y-0.5 pl-5 text-muted-foreground">
        <li>
          Seu perfil, {count(preview.transactions, 'lançamento', 'lançamentos')},{' '}
          {count(preview.recurring, 'fixo', 'fixos')}, {count(preview.categories, 'categoria', 'categorias')} e{' '}
          {count(preview.tasks, 'tarefa pessoal', 'tarefas pessoais')}
        </li>
        {preview.groups.map((g) => <li key={g.id}>{groupText(g)}</li>)}
      </ul>
    </div>
  )
}

/** O que acontece num grupo: sai (levando os itens), passa o admin ou o grupo some. */
function groupText(g: GroupImpact) {
  if (g.deletesGroup) return `Grupo “${g.name}”: só tem você, então é excluído.`
  const items = g.transactions + g.recurring + g.planItems
  const leaving = items
    ? `${count(items, 'item seu sai', 'itens seus saem')} do grupo (lançamentos, fixos e planejamento)`
    : 'você sai do grupo'
  const admin = g.newAdmin ? `; ${g.newAdmin.name} vira administrador` : ''
  return `Grupo “${g.name}”: ${leaving}${admin}. Os membros são avisados.`
}
