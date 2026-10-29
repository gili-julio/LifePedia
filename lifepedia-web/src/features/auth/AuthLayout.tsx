import { HeartPulseIcon, TargetIcon, WalletIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Logo } from '@/components/Logo'
import { LegalLinks } from '@/features/legal/LegalLinks'

const highlights = [
  { icon: WalletIcon, title: 'Finanças', text: 'Ganhos e despesas fixas, lançamentos do dia a dia e o saldo do mês.' },
  { icon: TargetIcon, title: 'Metas', text: 'Em breve: objetivos pessoais acompanhados de perto.' },
  { icon: HeartPulseIcon, title: 'Saúde e rotina', text: 'Em breve: hábitos, consultas e tudo que importa.' },
]

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-svh lg:grid-cols-2">
      <aside className="hidden flex-col justify-between bg-muted p-10 lg:flex">
        <Logo className="text-lg" />
        <div className="max-w-md space-y-8">
          <div className="space-y-3">
            <h1 className="text-3xl font-semibold tracking-tight">Sua vida inteira, organizada num só lugar.</h1>
            <p className="text-muted-foreground">
              O LifePedia centraliza seus dados pessoais para você enxergar o todo e decidir melhor.
            </p>
          </div>
          <ul className="space-y-5">
            {highlights.map(({ icon: Icon, title, text }) => (
              <li key={title} className="flex gap-3">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg border bg-background">
                  <Icon className="size-4" />
                </span>
                <div>
                  <p className="font-medium">{title}</p>
                  <p className="text-sm text-muted-foreground">{text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
        <p className="text-xs text-muted-foreground">© {new Date().getFullYear()} LifePedia</p>
      </aside>
      <main className="flex flex-col items-center justify-center gap-8 p-6">
        <Logo className="text-lg lg:hidden" />
        <div className="w-full max-w-sm">{children}</div>
        <LegalLinks />
      </main>
    </div>
  )
}
