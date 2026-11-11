import { PanelLeftIcon, PanelLeftCloseIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { useSidebar } from '@/components/ui/sidebar'
import { Switch } from '@/components/ui/switch'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { useAuth } from './auth-context'
import { MODULES, type Preferences, savePreferences } from './preferences'
import type { AppModule } from './types'

const STYLES = [
  { icons: false, label: 'Completo', hint: 'Nomes e seções, como sempre foi.', icon: PanelLeftIcon },
  { icons: true, label: 'Só ícones', hint: 'Barra estreita; os nomes e subitens aparecem ao passar o mouse.', icon: PanelLeftCloseIcon },
]

/** Formato do menu (no computador) e módulos que aparecem no menu e no Início; grava na conta a cada mudança. */
export function MenuPreferences() {
  const { user, setUser } = useAuth()
  const { setOpen, isMobile } = useSidebar()
  const [saving, setSaving] = useState(false)
  if (!user) return null
  const preferences = user.preferences
  const hidden = new Set(preferences.hiddenModules)

  async function save(next: Preferences) {
    const previous = user!
    setUser({ ...previous, preferences: next })
    setSaving(true)
    try {
      setUser(await savePreferences(next))
    } catch (error) {
      setUser(previous)
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar o menu.')
    } finally {
      setSaving(false)
    }
  }

  function pickStyle(icons: boolean) {
    if (icons === preferences.sidebarIcons) return
    if (!isMobile) setOpen(!icons)
    void save({ ...preferences, sidebarIcons: icons })
  }

  function toggleModule(id: AppModule, visible: boolean) {
    const next = visible ? preferences.hiddenModules.filter((m) => m !== id) : [...preferences.hiddenModules, id]
    void save({ ...preferences, hiddenModules: next })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Menu</CardTitle>
        <CardDescription>Vale para a sua conta, em todos os aparelhos.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="space-y-2">
          <Label id="menu-style">Formato no computador</Label>
          <div role="radiogroup" aria-labelledby="menu-style" className="grid gap-2 sm:grid-cols-2">
            {STYLES.map((style) => {
              const active = preferences.sidebarIcons === style.icons
              return (
                <button key={style.label} type="button" role="radio" aria-checked={active} disabled={saving}
                  onClick={() => pickStyle(style.icons)}
                  className={cn('flex items-start gap-3 rounded-xl border p-3 text-left transition-colors motion-reduce:transition-none hover:bg-muted',
                    active && 'border-primary bg-muted')}>
                  <style.icon className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden />
                  <span>
                    <span className="block text-sm font-medium">{style.label}</span>
                    <span className="block text-xs text-muted-foreground">{style.hint}</span>
                  </span>
                </button>
              )
            })}
          </div>
          <p className="text-xs text-muted-foreground">No celular o menu é sempre a gaveta. Ctrl+B troca na hora, só nesta tela.</p>
        </div>

        <div className="space-y-2">
          <Label>Módulos</Label>
          <p className="text-xs text-muted-foreground">
            Desligado, o módulo some do menu e do Início. Nada é apagado: ligue de novo quando quiser.
          </p>
          <ul className="divide-y rounded-xl border">
            {MODULES.map((module) => (
              <li key={module.id} className="flex items-center gap-3 px-3 py-2">
                <div className="min-w-0 flex-1">
                  <Label htmlFor={`module-${module.id}`} className="text-sm">{module.label}</Label>
                  <p className="truncate text-xs text-muted-foreground">{module.hint}</p>
                </div>
                <Switch id={`module-${module.id}`} checked={!hidden.has(module.id)} disabled={saving}
                  onCheckedChange={(on) => toggleModule(module.id, on)} />
              </li>
            ))}
          </ul>
        </div>
      </CardContent>
    </Card>
  )
}
