import { Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { DialogBody, DialogScrollContent } from '@/components/DialogLayout'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { useGroups } from '@/features/groups/api'
import { ApiError } from '@/lib/api'
import { amountToInput, parseAmount } from '@/lib/format'
import { useCreateWatch, useUpdateWatch } from '../api'
import type { WatchSummary } from '../types'

const DROPS = [5, 10, 15, 20, 30]
const PERSONAL = 'personal'

/** Valores iniciais de um item novo (ex.: vindo do planejamento ou do botão do navegador). */
export type WatchDefaults = { name?: string; groupId?: string | null; planItemId?: string | null; link?: string | null }

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Editar; sem ele, cria. */
  watch?: WatchSummary
  defaults?: WatchDefaults
}

/** Criar ou editar um item acompanhado: nome, links (na criação), preço-alvo, queda que avisa e opções do grupo. */
export function WatchDialog({ open, onOpenChange, watch, defaults }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogScrollContent className="sm:max-w-lg">
        {open && <WatchForm watch={watch} defaults={defaults} onClose={() => onOpenChange(false)} />}
      </DialogScrollContent>
    </Dialog>
  )
}

function WatchForm({ watch, defaults, onClose }: { watch?: WatchSummary; defaults?: WatchDefaults; onClose: () => void }) {
  const navigate = useNavigate()
  const create = useCreateWatch()
  const update = useUpdateWatch()
  const groups = useGroups()
  const [name, setName] = useState(watch?.name ?? defaults?.name ?? '')
  const [groupId, setGroupId] = useState<string | null>(watch?.groupId ?? defaults?.groupId ?? null)
  const [links, setLinks] = useState(defaults?.link ?? '')
  const [target, setTarget] = useState(watch?.targetPrice ? amountToInput(watch.targetPrice) : '')
  const [drop, setDrop] = useState(String(watch?.dropPercent ?? 10))
  const [notifyGroup, setNotifyGroup] = useState(watch?.notifyGroup ?? false)
  const planItemId = watch?.planItemId ?? defaults?.planItemId ?? null
  const [updatePlan, setUpdatePlan] = useState(watch?.updatePlan ?? !!planItemId)
  const [active, setActive] = useState(watch?.active ?? true)
  const targetValue = target.trim() ? parseAmount(target) : null
  const invalidTarget = targetValue !== null && (Number.isNaN(targetValue) || targetValue <= 0)
  const pending = create.isPending || update.isPending
  const fixedGroup = !!watch || !!planItemId

  async function save() {
    const settings = { targetPrice: targetValue, dropPercent: Number(drop), notifyGroup, updatePlan, active }
    try {
      if (watch) {
        await update.mutateAsync({ id: watch.id, name: name.trim(), settings })
        toast.success('Item salvo.')
        onClose()
        return
      }
      const urls = links.split(/\s+/).map((l) => l.trim()).filter(Boolean).slice(0, 5)
      const created = await create.mutateAsync({ name: name.trim(), groupId, planItemId, settings, links: urls })
      toast.success(created.watch.bestPrice ? 'Item acompanhado. Já achamos preços.' : 'Item acompanhado.')
      onClose()
      navigate(`/precos/${created.watch.id}`)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{watch ? 'Editar item' : 'Acompanhar preço'}</DialogTitle>
        <DialogDescription>
          Buscamos o produto no Buscapé (lojas e histórico) e nos links que você colar. A consulta é diária e avisa quando o preço
          chega no alvo ou cai.
        </DialogDescription>
      </DialogHeader>
      <DialogBody>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="watch-name">Produto</FieldLabel>
            <Input id="watch-name" value={name} maxLength={160} placeholder="Ex.: Fone JBL Tune 520BT" onChange={(e) => setName(e.target.value)} />
            <FieldDescription>Marca e modelo ajudam a achar o produto certo.</FieldDescription>
          </Field>
          {!fixedGroup && (
            <Field>
              <FieldLabel htmlFor="watch-group">Onde</FieldLabel>
              <Select value={groupId ?? PERSONAL} onValueChange={(v) => setGroupId(v === PERSONAL ? null : v)}>
                <SelectTrigger id="watch-group" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={PERSONAL}>Minha lista de desejos</SelectItem>
                  {groups.data?.map((g) => <SelectItem key={g.id} value={g.id}>Grupo {g.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          )}
          {!watch && (
            <Field>
              <FieldLabel htmlFor="watch-links">Links de lojas (opcional)</FieldLabel>
              <Textarea id="watch-links" value={links} rows={2} placeholder="Um por linha (até 5)" onChange={(e) => setLinks(e.target.value)} />
              <FieldDescription>Algumas lojas não deixam ler o preço; nesse caso use o botão do navegador ou anote.</FieldDescription>
            </Field>
          )}
          <div className="grid grid-cols-2 gap-3">
            <Field>
              <FieldLabel htmlFor="watch-target">Preço-alvo</FieldLabel>
              <Input id="watch-target" inputMode="decimal" value={target} placeholder="Opcional" aria-invalid={invalidTarget}
                onChange={(e) => setTarget(e.target.value)} />
            </Field>
            <Field>
              <FieldLabel htmlFor="watch-drop">Avisar se cair</FieldLabel>
              <Select value={drop} onValueChange={setDrop}>
                <SelectTrigger id="watch-drop" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {DROPS.map((d) => <SelectItem key={d} value={String(d)}>{d}% ou mais</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          </div>
          {groupId && (
            <Toggle id="watch-notify" checked={notifyGroup} onChange={setNotifyGroup} label="Avisar todo o grupo"
              hint="Sem isso, só quem criou recebe o aviso." />
          )}
          {planItemId && (
            <Toggle id="watch-plan" checked={updatePlan} onChange={setUpdatePlan} label="Atualizar o valor no planejamento"
              hint="O item do planejamento passa a valer o menor preço encontrado." />
          )}
          {watch && <Toggle id="watch-active" checked={active} onChange={setActive} label="Consultar todo dia" hint="Desligado, o item fica parado." />}
        </FieldGroup>
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Cancelar</Button>
        <Button onClick={() => void save()} disabled={!name.trim() || invalidTarget || pending}>
          {pending && <Loader2Icon className="animate-spin" />}
          {watch ? 'Salvar' : 'Acompanhar'}
        </Button>
      </DialogFooter>
    </>
  )
}

function Toggle({ id, checked, onChange, label, hint }: {
  id: string
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  hint: string
}) {
  return (
    <Field orientation="horizontal" className="justify-between">
      <div className="space-y-0.5">
        <FieldLabel htmlFor={id}>{label}</FieldLabel>
        <FieldDescription>{hint}</FieldDescription>
      </div>
      <Switch id={id} checked={checked} onCheckedChange={onChange} />
    </Field>
  )
}
