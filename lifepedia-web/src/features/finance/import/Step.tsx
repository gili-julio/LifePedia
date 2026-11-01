import type { ReactNode } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

type Props = { n: number; title: string; description: string; children: ReactNode }

/** Etapa numerada da importação. */
export function Step({ n, title, description, children }: Props) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <span className="flex size-6 items-center justify-center rounded-full bg-primary text-xs text-primary-foreground">{n}</span>
          {title}
        </CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">{children}</CardContent>
    </Card>
  )
}
