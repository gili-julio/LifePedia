import { ArrowLeftIcon } from 'lucide-react'
import { type ReactNode, useEffect } from 'react'
import { Link } from 'react-router'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/auth-context'
import { legalDate } from './legal'
import { LegalLinks } from './LegalLinks'

/** Página pública de texto legal (abre com ou sem sessão). */
export function LegalLayout({ title, summary, children }: { title: string; summary: string; children: ReactNode }) {
  const { user } = useAuth()
  useEffect(() => {
    document.title = `${title} · LifePedia`
    return () => {
      document.title = 'LifePedia'
    }
  }, [title])

  return (
    <div className="min-h-svh bg-background">
      <header className="sticky top-0 z-10 border-b bg-background/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-3xl items-center justify-between gap-3 px-4">
          <Link to="/" aria-label="LifePedia, ir para o início"><Logo /></Link>
          <Button variant="ghost" size="sm" asChild>
            <Link to={user ? '/' : '/login'}><ArrowLeftIcon /> {user ? 'Voltar ao app' : 'Entrar'}</Link>
          </Button>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-10">
        <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-2 text-sm text-muted-foreground">Vigente desde {legalDate()}</p>
        <p className="mt-6 rounded-xl border bg-muted/50 p-4 leading-relaxed">{summary}</p>
        <div className="mt-8 space-y-8">{children}</div>
        <footer className="mt-12 border-t pt-6">
          <LegalLinks />
        </footer>
      </main>
    </div>
  )
}

export function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
      <div className="space-y-3 leading-relaxed text-muted-foreground [&_strong]:font-medium [&_strong]:text-foreground">
        {children}
      </div>
    </section>
  )
}

export function List({ children }: { children: ReactNode }) {
  return <ul className="list-disc space-y-1.5 pl-5">{children}</ul>
}
