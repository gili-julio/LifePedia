import { Link } from 'react-router'
import { cn } from '@/lib/utils'

/** "Termos de uso · Política de privacidade" (rodapé do login, cadastro, perfil e das próprias páginas). */
export function LegalLinks({ className, newTab = false }: { className?: string; newTab?: boolean }) {
  const target = newTab ? { target: '_blank', rel: 'noopener' } : {}
  return (
    <p className={cn('flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-xs text-muted-foreground', className)}>
      <Link to="/termos" className="underline-offset-4 hover:text-foreground hover:underline" {...target}>Termos de uso</Link>
      <span aria-hidden>·</span>
      <Link to="/privacidade" className="underline-offset-4 hover:text-foreground hover:underline" {...target}>
        Política de privacidade
      </Link>
    </p>
  )
}
