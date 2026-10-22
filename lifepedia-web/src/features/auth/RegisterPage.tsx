import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2Icon } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api'
import { AuthLayout } from './AuthLayout'
import { returnTo, useAuth } from './auth-context'
import { usernameSchema } from './username'
import { UsernameInput } from './UsernameInput'

const schema = z
  .object({
    name: z.string().trim().min(1, 'Informe seu nome.').max(120),
    username: usernameSchema,
    email: z.email('Informe um e-mail válido.'),
    password: z.string().min(8, 'A senha deve ter pelo menos 8 caracteres.').max(72, 'Máximo de 72 caracteres.'),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    path: ['confirmPassword'],
    message: 'As senhas não conferem.',
  })

type FormInput = z.input<typeof schema>
type FormValues = z.output<typeof schema>

export function RegisterPage() {
  const { register: registerUser } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', username: '', email: '', password: '', confirmPassword: '' },
  })
  const { errors, isSubmitting } = form.formState

  async function onSubmit({ name, username, email, password }: FormValues) {
    try {
      await registerUser({ name, username, email, password })
      toast.success(`Bem-vindo(a), ${name.split(' ')[0]}!`)
      navigate(returnTo(location.state), { replace: true })
    } catch (error) {
      if (error instanceof ApiError) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          form.setError(field as keyof FormInput, { message })
        }
        toast.error(error.message)
      } else {
        toast.error('Não foi possível criar a conta.')
      }
    }
  }

  return (
    <AuthLayout>
      <Card>
        <CardHeader>
          <CardTitle className="text-xl">Criar conta</CardTitle>
          <CardDescription>Leva menos de um minuto.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
            <FieldGroup>
              <Field data-invalid={!!errors.name}>
                <FieldLabel htmlFor="name">Nome</FieldLabel>
                <Input id="name" autoComplete="name" aria-invalid={!!errors.name} {...form.register('name')} />
                <FieldError errors={[errors.name]} />
              </Field>
              <Field data-invalid={!!errors.username}>
                <FieldLabel htmlFor="username">Nome de usuário</FieldLabel>
                <UsernameInput id="username" aria-invalid={!!errors.username} {...form.register('username')} />
                {errors.username ? (
                  <FieldError errors={[errors.username]} />
                ) : (
                  <FieldDescription>É assim que as pessoas te encontram para convites.</FieldDescription>
                )}
              </Field>
              <Field data-invalid={!!errors.email}>
                <FieldLabel htmlFor="email">E-mail</FieldLabel>
                <Input id="email" type="email" autoComplete="email" placeholder="voce@exemplo.com"
                  aria-invalid={!!errors.email} {...form.register('email')} />
                <FieldError errors={[errors.email]} />
              </Field>
              <Field data-invalid={!!errors.password}>
                <FieldLabel htmlFor="password">Senha</FieldLabel>
                <Input id="password" type="password" autoComplete="new-password"
                  aria-invalid={!!errors.password} {...form.register('password')} />
                {errors.password ? (
                  <FieldError errors={[errors.password]} />
                ) : (
                  <FieldDescription>Mínimo de 8 caracteres.</FieldDescription>
                )}
              </Field>
              <Field data-invalid={!!errors.confirmPassword}>
                <FieldLabel htmlFor="confirmPassword">Confirmar senha</FieldLabel>
                <Input id="confirmPassword" type="password" autoComplete="new-password"
                  aria-invalid={!!errors.confirmPassword} {...form.register('confirmPassword')} />
                <FieldError errors={[errors.confirmPassword]} />
              </Field>
              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting && <Loader2Icon className="animate-spin" />}
                Criar conta
              </Button>
              <p className="text-center text-sm text-muted-foreground">
                Já tem conta?{' '}
                <Link to="/login" state={location.state} className="font-medium text-foreground underline underline-offset-4">
                  Entrar
                </Link>
              </p>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </AuthLayout>
  )
}
