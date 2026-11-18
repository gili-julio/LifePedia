import { BookmarkPlusIcon, Loader2Icon } from 'lucide-react'
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useGroups } from '@/features/groups/api'
import { ApiError } from '@/lib/api'
import { amountToInput, parseAmount } from '@/lib/format'
import { useAddWatchLink, useAddWatchPrice, useCreateWatch, usePriceWatches } from '../api'

const NEW = 'new'
const PERSONAL = 'personal'

/**
 * Chegada do botão do navegador (`?url=&nome=&preco=&loja=`): confere os dados e cria o item ou junta a um existente. Nada
 * é gravado sem confirmar.
 */
export function CapturePage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const watches = usePriceWatches()
  const groups = useGroups()
  const create = useCreateWatch()
  const addLink = useAddWatchLink()
  const addPrice = useAddWatchPrice()
  const url = safeUrl(params.get('url'))
  const captured = parseAmount(params.get('preco') ?? '')
  const [target, setTarget] = useState(NEW)
  const [groupId, setGroupId] = useState<string>(PERSONAL)
  const [name, setName] = useState((params.get('nome') ?? '').slice(0, 160))
  const [store, setStore] = useState((params.get('loja') ?? '').slice(0, 80))
  const [price, setPrice] = useState(Number.isNaN(captured) ? '' : amountToInput(captured))
  const value = price.trim() ? parseAmount(price) : null
  const pending = create.isPending || addLink.isPending || addPrice.isPending

  async function save() {
    const manual = value !== null && !Number.isNaN(value) && value > 0 && store.trim()
      ? { store: store.trim(), price: value, url: url ?? undefined } : undefined
    try {
      if (target === NEW) {
        const created = await create.mutateAsync({ name: name.trim(), groupId: groupId === PERSONAL ? null : groupId,
          links: url ? [url] : [], price: manual })
        navigate(`/precos/${created.watch.id}`, { replace: true })
      } else {
        if (url) await addLink.mutateAsync({ id: target, url }).catch((e: unknown) => {
          if (!(e instanceof ApiError && e.status === 409)) throw e
        })
        if (manual) await addPrice.mutateAsync({ id: target, ...manual })
        navigate(`/precos/${target}`, { replace: true })
      }
      toast.success('Preço salvo.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <PageHeader title="Salvar preço" description="Veio do botão do navegador. Confira antes de salvar." />
      <Card>
        <CardContent>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="capture-target">Item</FieldLabel>
              <Select value={target} onValueChange={setTarget}>
                <SelectTrigger id="capture-target" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NEW}>Novo item</SelectItem>
                  {watches.data?.map((w) => <SelectItem key={w.id} value={w.id}>{w.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
            {target === NEW && (
              <>
                <Field>
                  <FieldLabel htmlFor="capture-name">Produto</FieldLabel>
                  <Input id="capture-name" value={name} maxLength={160} onChange={(e) => setName(e.target.value)} />
                </Field>
                <Field>
                  <FieldLabel htmlFor="capture-group">Onde</FieldLabel>
                  <Select value={groupId} onValueChange={setGroupId}>
                    <SelectTrigger id="capture-group" className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value={PERSONAL}>Minha lista de desejos</SelectItem>
                      {groups.data?.map((g) => <SelectItem key={g.id} value={g.id}>Grupo {g.name}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </Field>
              </>
            )}
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel htmlFor="capture-store">Loja</FieldLabel>
                <Input id="capture-store" value={store} maxLength={80} onChange={(e) => setStore(e.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="capture-price">Preço</FieldLabel>
                <Input id="capture-price" inputMode="decimal" value={price} placeholder="Não achado" onChange={(e) => setPrice(e.target.value)} />
              </Field>
            </div>
            <Field>
              <FieldLabel>Link</FieldLabel>
              <FieldDescription className="break-all">{url ?? 'Sem link válido.'}</FieldDescription>
            </Field>
          </FieldGroup>
        </CardContent>
      </Card>
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={() => navigate('/precos')}>Cancelar</Button>
        <Button onClick={() => void save()} disabled={pending || (target === NEW && !name.trim())}>
          {pending ? <Loader2Icon className="animate-spin" /> : <BookmarkPlusIcon />} Salvar
        </Button>
      </div>
    </div>
  )
}

function safeUrl(value: string | null) {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.toString().slice(0, 2048) : null
  } catch {
    return null
  }
}
