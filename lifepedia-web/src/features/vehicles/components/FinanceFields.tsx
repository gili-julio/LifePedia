import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { CategorySelect } from '@/features/finance/components/CategorySelect'
import { useGroups } from '@/features/groups/api'
import type { FinanceState } from '../finance-link'

/** "Lançar no meu financeiro" + categoria e, ao criar, grupos para onde o lançamento vai sincronizado. */
export function FinanceFields({ state, onChange, creating, idPrefix }: {
  state: FinanceState
  onChange: (state: FinanceState) => void
  creating: boolean
  idPrefix: string
}) {
  const groups = useGroups()
  const mine = (groups.data ?? []).filter((g) => g.role)
  return (
    <div className="space-y-3 rounded-2xl border p-3">
      <div className="flex items-center justify-between gap-3">
        <Label htmlFor={`${idPrefix}-fin`} className="font-medium">Lançar no meu financeiro</Label>
        <Switch id={`${idPrefix}-fin`} checked={state.on} onCheckedChange={(on) => onChange({ ...state, on })} />
      </div>
      {state.on && (
        <>
          <Field>
            <FieldLabel htmlFor={`${idPrefix}-cat`}>Categoria</FieldLabel>
            <CategorySelect id={`${idPrefix}-cat`} type="EXPENSE" value={state.categoryId}
              onChange={(categoryId) => onChange({ ...state, categoryId })} />
            <FieldDescription>Vira uma despesa sua, com o nome do veículo. Mudar ou excluir aqui muda lá também.</FieldDescription>
          </Field>
          {creating && mine.length > 0 && (
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium">Levar também para o grupo</legend>
              {mine.map((g) => (
                <label key={g.id} className="flex items-center gap-2 text-sm">
                  <Checkbox checked={state.groupIds.includes(g.id)} onCheckedChange={(checked) => onChange({
                    ...state,
                    groupIds: checked ? [...state.groupIds, g.id] : state.groupIds.filter((id) => id !== g.id),
                  })} />
                  {g.name}
                </label>
              ))}
              <p className="text-xs text-muted-foreground">Entra no financeiro do grupo sincronizado com o seu.</p>
            </fieldset>
          )}
        </>
      )}
    </div>
  )
}
