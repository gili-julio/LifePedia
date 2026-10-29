import { useEditorState, type Editor } from '@tiptap/react'
import {
  BoldIcon,
  ChevronDownIcon,
  CodeIcon,
  CodeSquareIcon,
  ImageIcon,
  ItalicIcon,
  LinkIcon,
  ListChecksIcon,
  ListIcon,
  ListOrderedIcon,
  MinusIcon,
  Redo2Icon,
  StrikethroughIcon,
  TableIcon,
  TextQuoteIcon,
  Undo2Icon,
  type LucideIcon,
} from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

const BLOCKS = [
  { level: 0, label: 'Texto' },
  { level: 1, label: 'Título 1' },
  { level: 2, label: 'Título 2' },
  { level: 3, label: 'Título 3' },
] as const

/** Botões de formatação do modo visual (o estado ativo vem do editor). */
export function Toolbar({ editor, onImage }: { editor: Editor; onImage: () => void }) {
  const state = useEditorState({
    editor,
    selector: ({ editor: e }) => ({
      level: ([1, 2, 3] as const).find((level) => e.isActive('heading', { level })) ?? 0,
      bold: e.isActive('bold'),
      italic: e.isActive('italic'),
      strike: e.isActive('strike'),
      code: e.isActive('code'),
      bullet: e.isActive('bulletList'),
      ordered: e.isActive('orderedList'),
      task: e.isActive('taskList'),
      quote: e.isActive('blockquote'),
      codeBlock: e.isActive('codeBlock'),
      link: e.isActive('link'),
      table: e.isActive('table'),
      canUndo: e.can().undo(),
      canRedo: e.can().redo(),
    }),
  })
  const chain = () => editor.chain().focus()
  const block = BLOCKS.find((b) => b.level === state.level) ?? BLOCKS[0]

  return (
    <div role="toolbar" aria-label="Formatação" className="flex items-center gap-0.5 overflow-x-auto py-1">
      <Tool icon={Undo2Icon} label="Desfazer (Ctrl+Z)" disabled={!state.canUndo} onClick={() => chain().undo().run()} />
      <Tool icon={Redo2Icon} label="Refazer (Ctrl+Shift+Z)" disabled={!state.canRedo} onClick={() => chain().redo().run()} />
      <Divider />
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button type="button" variant="ghost" size="sm" className="shrink-0 gap-1 px-2" aria-label={`Tipo do bloco: ${block.label}`}>
            {block.label} <ChevronDownIcon className="size-3.5" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          {BLOCKS.map((b) => (
            <DropdownMenuItem key={b.level}
              onClick={() => (b.level === 0 ? chain().setParagraph().run() : chain().setHeading({ level: b.level }).run())}>
              {b.label}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
      <Divider />
      <Tool icon={BoldIcon} label="Negrito (Ctrl+B)" active={state.bold} onClick={() => chain().toggleBold().run()} />
      <Tool icon={ItalicIcon} label="Itálico (Ctrl+I)" active={state.italic} onClick={() => chain().toggleItalic().run()} />
      <Tool icon={StrikethroughIcon} label="Riscado" active={state.strike} onClick={() => chain().toggleStrike().run()} />
      <Tool icon={CodeIcon} label="Código na linha" active={state.code} onClick={() => chain().toggleCode().run()} />
      <LinkTool editor={editor} active={state.link} />
      <Divider />
      <Tool icon={ListIcon} label="Lista" active={state.bullet} onClick={() => chain().toggleBulletList().run()} />
      <Tool icon={ListOrderedIcon} label="Lista numerada" active={state.ordered} onClick={() => chain().toggleOrderedList().run()} />
      <Tool icon={ListChecksIcon} label="Checklist" active={state.task} onClick={() => chain().toggleTaskList().run()} />
      <Divider />
      <Tool icon={TextQuoteIcon} label="Citação" active={state.quote} onClick={() => chain().toggleBlockquote().run()} />
      <Tool icon={CodeSquareIcon} label="Bloco de código" active={state.codeBlock} onClick={() => chain().toggleCodeBlock().run()} />
      <Tool icon={MinusIcon} label="Divisória" onClick={() => chain().setHorizontalRule().run()} />
      <TableTool editor={editor} active={state.table} />
      <Tool icon={ImageIcon} label="Imagem" onClick={onImage} />
    </div>
  )
}

function Tool({ icon: Icon, label, active, disabled, onClick }: {
  icon: LucideIcon
  label: string
  active?: boolean
  disabled?: boolean
  onClick: () => void
}) {
  return (
    <Button type="button" variant="ghost" size="icon-sm" aria-label={label} title={label} aria-pressed={active}
      disabled={disabled} onClick={onClick} className={cn('shrink-0', active && 'bg-accent text-accent-foreground')}>
      <Icon />
    </Button>
  )
}

function Divider() {
  return <span aria-hidden className="mx-1 h-5 w-px shrink-0 bg-border" />
}

/** Link no texto selecionado: só http(s) ou e-mail (abre em outra aba). */
function LinkTool({ editor, active }: { editor: Editor; active: boolean }) {
  const [open, setOpen] = useState(false)
  const [url, setUrl] = useState('')

  function onOpenChange(next: boolean) {
    setOpen(next)
    if (next) setUrl((editor.getAttributes('link').href as string | undefined) ?? '')
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const value = url.trim()
    const chain = editor.chain().focus().extendMarkRange('link')
    if (!value) chain.unsetLink().run()
    else chain.setLink({ href: /^(https?:\/\/|mailto:)/i.test(value) ? value : `https://${value}` }).run()
    setOpen(false)
  }

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        <Button type="button" variant="ghost" size="icon-sm" aria-label="Link" title="Link" aria-pressed={active}
          className={cn('shrink-0', active && 'bg-accent text-accent-foreground')}>
          <LinkIcon />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-72">
        <form onSubmit={onSubmit} className="space-y-2">
          <Input autoFocus value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://..."
            aria-label="Endereço do link" className="text-base md:text-sm" />
          <div className="flex justify-end gap-2">
            {active && <Button type="button" variant="ghost" size="sm" onClick={() => {
              editor.chain().focus().extendMarkRange('link').unsetLink().run()
              setOpen(false)
            }}>Tirar link</Button>}
            <Button type="submit" size="sm">Aplicar</Button>
          </div>
        </form>
      </PopoverContent>
    </Popover>
  )
}

/** Fora de tabela insere uma 3×3; dentro, abre as ações de linha e coluna. */
function TableTool({ editor, active }: { editor: Editor; active: boolean }) {
  if (!active) {
    return <Tool icon={TableIcon} label="Tabela" onClick={() => editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()} />
  }
  const chain = () => editor.chain().focus()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="ghost" size="sm" className="shrink-0 gap-1 bg-accent px-2" aria-label="Ações da tabela">
          <TableIcon /> <ChevronDownIcon className="size-3.5" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        <DropdownMenuItem onClick={() => chain().addRowBefore().run()}>Linha acima</DropdownMenuItem>
        <DropdownMenuItem onClick={() => chain().addRowAfter().run()}>Linha abaixo</DropdownMenuItem>
        <DropdownMenuItem onClick={() => chain().addColumnBefore().run()}>Coluna à esquerda</DropdownMenuItem>
        <DropdownMenuItem onClick={() => chain().addColumnAfter().run()}>Coluna à direita</DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => chain().deleteRow().run()}>Excluir linha</DropdownMenuItem>
        <DropdownMenuItem onClick={() => chain().deleteColumn().run()}>Excluir coluna</DropdownMenuItem>
        <DropdownMenuItem variant="destructive" onClick={() => chain().deleteTable().run()}>Excluir tabela</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
