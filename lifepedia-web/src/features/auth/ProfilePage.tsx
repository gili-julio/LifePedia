import { zodResolver } from '@hookform/resolvers/zod'
import { AlertTriangleIcon, Loader2Icon } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { AccountSections } from '@/features/account/components/AccountSections'
import { LegalLinks } from '@/features/legal/LegalLinks'
import { PushCard } from '@/features/notifications/PushCard'
import { api, ApiError } from '@/lib/api'
import { useAuth } from './auth-context'
import { GoogleAccount } from './GoogleAccount'
import { ProfilePhoto } from './ProfilePhoto'
import type { ProfileRequest, User } from './types'
import { usernameSchema } from './username'
import { UsernameInput } from './UsernameInput'

const schema = z.object({
  name: z.string().trim().min(1, 'Informe seu nome.').max(120, 'Máximo de 120 caracteres.'),
  username: usernameSchema,
})

type FormInput = z.input<typeof schema>
type FormValues = z.output<typeof schema>

/** Dados do usuário: nome e @username (e-mail só leitura). */
export function ProfilePage() {
  const { user, setUser } = useAuth()
  const navigate = useNavigate()
  const incomplete = !!user && !user.profileComplete
  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: user?.name ?? '', username: user?.username ?? '' },
  })
  const { errors, isSubmitting } = form.formState

  async function onSubmit(values: FormValues) {
    try {
      const updated = await api<User>('/api/users/me', { method: 'PUT', body: values satisfies ProfileRequest })
      setUser(updated)
      form.reset({ name: updated.name, username: updated.username ?? '' })
      toast.success('Cadastro atualizado.')
      if (incomplete) navigate('/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          form.setError(field as keyof FormInput, { message })
        }
        if (error.status === 409) form.setError('username', { message: error.message })
        toast.error(error.message)
      } else {
        toast.error('Não foi possível salvar.')
      }
    }
  }

  if (!user) return null

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <PageHeader title="Meu perfil" description="Como você aparece para outras pessoas nos grupos." />

      {incomplete && (
        <p className="flex gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm">
          <AlertTriangleIcon className="mt-0.5 size-4 shrink-0 text-amber-600 dark:text-amber-400" />
          Complete as informações obrigatórias para continuar usando o LifePedia.
        </p>
      )}

      <Card>
        <CardContent className="space-y-6">
          <ProfilePhoto />
          <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
            <FieldGroup>
              <Field data-invalid={!!errors.name}>
                <FieldLabel htmlFor="profile-name">Nome</FieldLabel>
                <Input id="profile-name" autoComplete="name" aria-invalid={!!errors.name} {...form.register('name')} />
                <FieldError errors={[errors.name]} />
              </Field>
              <Field data-invalid={!!errors.username}>
                <FieldLabel htmlFor="profile-username">Nome de usuário *</FieldLabel>
                <UsernameInput id="profile-username" aria-invalid={!!errors.username} autoFocus={incomplete}
                  {...form.register('username')} />
                {errors.username ? (
                  <FieldError errors={[errors.username]} />
                ) : (
                  <FieldDescription>Único no LifePedia. Usado para te encontrar e convidar para grupos.</FieldDescription>
                )}
              </Field>
              <Field>
                <FieldLabel htmlFor="profile-email">E-mail</FieldLabel>
                <Input id="profile-email" value={user.email} readOnly disabled />
              </Field>
              <div className="flex justify-end">
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting && <Loader2Icon className="animate-spin" />}
                  Salvar
                </Button>
              </div>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>

      <GoogleAccount />
      {!incomplete && <PushCard />}
      {!incomplete && <AccountSections />}
      <LegalLinks />
    </div>
  )
}
