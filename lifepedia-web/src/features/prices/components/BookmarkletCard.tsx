import { BookmarkPlusIcon, ChevronDownIcon } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { bookmarkletCode } from '../labels'

/**
 * Botão para a barra de favoritos: na página do produto, abre o LifePedia com o nome, o preço e o link para confirmar.
 * O React bloqueia `javascript:` no `href`, então o link é montado direto no elemento.
 */
export function BookmarkletCard() {
  const link = useRef<HTMLAnchorElement>(null)
  useEffect(() => {
    link.current?.setAttribute('href', bookmarkletCode(window.location.origin))
  }, [])

  return (
    <Card size="sm">
      <CardContent>
        <Collapsible>
          <CollapsibleTrigger className="group flex w-full items-center gap-2 text-left text-sm font-medium">
            <BookmarkPlusIcon className="size-4 text-muted-foreground" aria-hidden />
            <span className="flex-1">Botão do navegador: acompanhe direto da loja</span>
            <ChevronDownIcon className="size-4 text-muted-foreground transition-transform group-data-[state=open]:rotate-180" aria-hidden />
          </CollapsibleTrigger>
          <CollapsibleContent className="space-y-3 pt-3 text-sm text-muted-foreground">
            <p>
              Arraste o botão abaixo para a barra de favoritos do computador. Na página de um produto, clique nele: o LifePedia abre
              com o nome, o preço e o link para você confirmar. Funciona até em lojas que não deixam o servidor ler o preço.
            </p>
            <a ref={link} draggable onClick={(e) => e.preventDefault()}
              className="inline-flex cursor-grab items-center gap-2 rounded-full border border-dashed bg-muted px-3 py-1.5 font-medium text-foreground active:cursor-grabbing">
              <BookmarkPlusIcon className="size-4" aria-hidden /> + LifePedia preço
            </a>
            <p className="text-xs">
              No celular, favoritos com código não funcionam bem: use "Anotar preço" ou cole o link da loja.
            </p>
          </CollapsibleContent>
        </Collapsible>
      </CardContent>
    </Card>
  )
}
