import type { ComponentProps } from 'react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

/** Campo de @username (o @ fica fixo à esquerda). */
export function UsernameInput({ className, ...props }: ComponentProps<typeof Input>) {
  return (
    <div className="relative">
      <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-sm text-muted-foreground">@</span>
      <Input
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
        autoComplete="username"
        maxLength={31}
        placeholder="seu.nome"
        className={cn('pl-7 lowercase', className)}
        {...props}
      />
    </div>
  )
}
