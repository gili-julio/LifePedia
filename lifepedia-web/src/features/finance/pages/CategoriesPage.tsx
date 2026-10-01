import { EllipsisVerticalIcon, PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { ActionSheet } from '@/components/ActionSheet'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { CategoryDialog } from '../components/CategoryDialog'
import { DeleteCategoryDialog } from '../components/DeleteCategoryDialog'
import type { Category, EntryType } from '../types'
import { useCategoryLookup } from '../use-category-lookup'

function CategoryList({ items, onEdit, onDelete, onMore }: {
  items: Category[]
  onEdit: (c: Category) => void
  onDelete: (c: Category) => void
  onMore: (c: Category) => void
}) {
  if (!items.length) {
    return <p className="py-10 text-center text-sm text-muted-foreground">Nenhuma categoria ainda.</p>
  }
  return (
    <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
      {items.map((c) => (
        <li key={c.id} className="flex items-center gap-3 rounded-lg border bg-card px-3 py-2">
          <span className="size-8 shrink-0 rounded-md" style={{ background: c.color }} aria-hidden />
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium">{c.name}</p>
            <p className="text-xs text-muted-foreground">
              {c.usageCount === 0 ? 'Sem lançamentos' : `${c.usageCount} lançamento${c.usageCount > 1 ? 's' : ''}`}
            </p>
          </div>
          <Button variant="ghost" size="icon-sm" className="hidden md:inline-flex" aria-label={`Editar ${c.name}`} onClick={() => onEdit(c)}>
            <PencilIcon />
          </Button>
          <Button variant="ghost" size="icon-sm" className="hidden md:inline-flex" aria-label={`Excluir ${c.name}`} onClick={() => onDelete(c)}>
            <Trash2Icon />
          </Button>
          <Button variant="ghost" size="icon-sm" className="md:hidden" aria-label={`Opções de ${c.name}`} onClick={() => onMore(c)}>
            <EllipsisVerticalIcon />
          </Button>
        </li>
      ))}
    </ul>
  )
}

export function CategoriesPage() {
  const { ofType, isLoading } = useCategoryLookup()
  const [tab, setTab] = useState<EntryType>('EXPENSE')
  const [dialog, setDialog] = useState<{ open: boolean; category?: Category }>({ open: false })
  const [deleting, setDeleting] = useState<Category | null>(null)
  const [menu, setMenu] = useState<{ open: boolean; item?: Category }>({ open: false })

  const expenses = ofType('EXPENSE')
  const incomes = ofType('INCOME')
  const openEdit = (category: Category) => setDialog({ open: true, category })
  const listProps = { onEdit: openEdit, onDelete: setDeleting, onMore: (item: Category) => setMenu({ open: true, item }) }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Categorias"
        description="Organize seus lançamentos do seu jeito. As cores aparecem nos gráficos do comparativo."
        actions={[{ label: 'Nova categoria', icon: PlusIcon, primary: true, onSelect: () => setDialog({ open: true }) }]}
      />

      <Card>
        <CardContent>
          {isLoading ? (
            <Skeleton className="h-40" />
          ) : (
            <Tabs value={tab} onValueChange={(v) => setTab(v as EntryType)}>
              <TabsList>
                <TabsTrigger value="EXPENSE">Despesas ({expenses.length})</TabsTrigger>
                <TabsTrigger value="INCOME">Ganhos ({incomes.length})</TabsTrigger>
              </TabsList>
              <TabsContent value="EXPENSE" className="pt-4">
                <CategoryList items={expenses} {...listProps} />
              </TabsContent>
              <TabsContent value="INCOME" className="pt-4">
                <CategoryList items={incomes} {...listProps} />
              </TabsContent>
            </Tabs>
          )}
        </CardContent>
      </Card>

      <CategoryDialog
        open={dialog.open}
        onOpenChange={(open) => setDialog((d) => ({ ...d, open }))}
        category={dialog.category}
        defaultType={tab}
      />
      {menu.item && (
        <ActionSheet
          open={menu.open}
          onOpenChange={(open) => setMenu((m) => ({ ...m, open }))}
          title={menu.item.name}
          actions={[
            { label: 'Editar', icon: PencilIcon, onSelect: () => openEdit(menu.item!) },
            { label: 'Excluir', icon: Trash2Icon, destructive: true, onSelect: () => setDeleting(menu.item!) },
          ]}
        />
      )}
      <DeleteCategoryDialog category={deleting} onOpenChange={(open) => !open && setDeleting(null)} />
    </div>
  )
}
