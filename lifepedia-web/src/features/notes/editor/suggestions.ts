import { PluginKey } from '@tiptap/pm/state'
import { Extension, ReactRenderer, type Editor, type Range } from '@tiptap/react'
import { Suggestion, type SuggestionKeyDownProps, type SuggestionOptions, type SuggestionProps } from '@tiptap/suggestion'
import {
  CodeSquareIcon,
  Heading1Icon,
  Heading2Icon,
  Heading3Icon,
  ImageIcon,
  LinkIcon,
  ListChecksIcon,
  ListIcon,
  ListOrderedIcon,
  MinusIcon,
  PlusIcon,
  TableIcon,
  TextQuoteIcon,
} from 'lucide-react'
import type { NoteRef } from '../types'
import { SuggestionMenu, type SuggestionItem, type SuggestionMenuHandle } from './SuggestionMenu'

type Run = (editor: Editor, range: Range) => void
type Item = SuggestionItem & { keywords?: string; run: Run }

const MENU_WIDTH = 256
const GAP = 6

export type SlashOptions = { onImage: () => void }

/** Digitar "/" abre os blocos (título, lista, checklist, tabela...). */
export const SlashCommand = Extension.create<SlashOptions>({
  name: 'slashCommand',

  addOptions() {
    return { onImage: () => undefined }
  },

  addProseMirrorPlugins() {
    const options = this.options
    const blocks: Item[] = [
      { id: 'h1', label: 'Título 1', hint: '#', icon: Heading1Icon, keywords: 'titulo heading',
        run: (e, r) => e.chain().focus().deleteRange(r).setNode('heading', { level: 1 }).run() },
      { id: 'h2', label: 'Título 2', hint: '##', icon: Heading2Icon, keywords: 'titulo subtitulo heading',
        run: (e, r) => e.chain().focus().deleteRange(r).setNode('heading', { level: 2 }).run() },
      { id: 'h3', label: 'Título 3', hint: '###', icon: Heading3Icon, keywords: 'titulo heading',
        run: (e, r) => e.chain().focus().deleteRange(r).setNode('heading', { level: 3 }).run() },
      { id: 'bullet', label: 'Lista', hint: '-', icon: ListIcon, keywords: 'marcadores topicos',
        run: (e, r) => e.chain().focus().deleteRange(r).toggleBulletList().run() },
      { id: 'ordered', label: 'Lista numerada', hint: '1.', icon: ListOrderedIcon, keywords: 'numeros',
        run: (e, r) => e.chain().focus().deleteRange(r).toggleOrderedList().run() },
      { id: 'task', label: 'Checklist', hint: '[ ]', icon: ListChecksIcon, keywords: 'tarefas caixas todo',
        run: (e, r) => e.chain().focus().deleteRange(r).toggleTaskList().run() },
      { id: 'quote', label: 'Citação', hint: '>', icon: TextQuoteIcon, keywords: 'citacao quote',
        run: (e, r) => e.chain().focus().deleteRange(r).toggleBlockquote().run() },
      { id: 'code', label: 'Bloco de código', hint: '```', icon: CodeSquareIcon, keywords: 'codigo programacao',
        run: (e, r) => e.chain().focus().deleteRange(r).toggleCodeBlock().run() },
      { id: 'table', label: 'Tabela', icon: TableIcon, keywords: 'tabela colunas linhas',
        run: (e, r) => e.chain().focus().deleteRange(r).insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run() },
      { id: 'hr', label: 'Divisória', hint: '---', icon: MinusIcon, keywords: 'linha separador',
        run: (e, r) => e.chain().focus().deleteRange(r).setHorizontalRule().run() },
      { id: 'image', label: 'Imagem', icon: ImageIcon, keywords: 'foto imagem',
        run: (e, r) => {
          e.chain().focus().deleteRange(r).run()
          options.onImage()
        } },
      { id: 'note-link', label: 'Link para nota', hint: '[[', icon: LinkIcon, keywords: 'link nota wiki',
        run: (e, r) => e.chain().focus().deleteRange(r).insertContent('[[').run() },
    ]
    return [Suggestion<Item, Item>({
      editor: this.editor,
      pluginKey: new PluginKey('slashCommand'),
      char: '/',
      items: ({ query }) => {
        const q = normalize(query)
        return blocks.filter((b) => normalize(`${b.label} ${b.keywords ?? ''}`).includes(q))
      },
      command: ({ editor, range, props }) => props.run(editor, range),
      render: menu('Nenhum bloco com esse nome'),
    })]
  },
})

export type WikiSuggestOptions = { refs: () => NoteRef[] }

/** Digitar `[[` sugere as notas pelo título; sem nenhuma igual, oferece criar o link assim mesmo. */
export const WikiSuggest = Extension.create<WikiSuggestOptions>({
  name: 'wikiSuggest',

  addOptions() {
    return { refs: () => [] }
  },

  addProseMirrorPlugins() {
    const options = this.options
    return [Suggestion<Item, Item>({
      editor: this.editor,
      pluginKey: new PluginKey('wikiSuggest'),
      char: '[[',
      allowSpaces: true,
      allowedPrefixes: null,
      allow: ({ state, range }) => !state.doc.textBetween(range.from, range.to).slice(2).includes(']'),
      items: ({ query }) => {
        const q = normalize(query.trim())
        const found = options.refs().filter((n) => normalize(n.title).includes(q)).slice(0, 8)
        const title = query.trim().replace(/[[\]]/g, '')
        const items: Item[] = found.map((n) => ({ id: n.id, label: n.title, emoji: n.icon, run: insertLink(n.title) }))
        if (title && !found.some((n) => normalize(n.title) === q)) {
          items.push({ id: 'new', label: `Ligar a "${title}"`, hint: 'nova', icon: PlusIcon, run: insertLink(title) })
        }
        return items
      },
      command: ({ editor, range, props }) => props.run(editor, range),
      render: menu('Digite o nome da nota'),
    })]
  },
})

function insertLink(title: string): Run {
  return (editor, range) => editor.chain().focus().deleteRange(range).insertContent(`[[${title}]] `).run()
}

function normalize(text: string) {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
}

/** Abre a lista no cursor (embaixo; em cima se não couber). */
function menu(empty: string): SuggestionOptions<Item, Item>['render'] {
  return () => {
    let renderer: ReactRenderer<SuggestionMenuHandle> | null = null
    const close = () => {
      renderer?.destroy()
      renderer?.element.remove()
      renderer = null
    }
    const place = (props: SuggestionProps<Item, Item>) => {
      const rect = props.clientRect?.()
      const element = renderer?.element as HTMLElement | undefined
      if (!rect || !element) return
      element.style.position = 'fixed'
      element.style.zIndex = '60'
      element.style.left = `${Math.max(8, Math.min(rect.left, window.innerWidth - MENU_WIDTH - 8))}px`
      const height = element.offsetHeight
      const below = rect.bottom + GAP
      element.style.top = `${below + height > window.innerHeight ? Math.max(8, rect.top - height - GAP) : below}px`
    }
    return {
      onStart: (props) => {
        renderer = new ReactRenderer(SuggestionMenu, {
          props: { items: props.items, command: props.command, empty },
          editor: props.editor,
        })
        document.body.appendChild(renderer.element)
        place(props)
      },
      onUpdate: (props) => {
        renderer?.updateProps({ items: props.items, command: props.command })
        place(props)
      },
      onKeyDown: ({ event }: SuggestionKeyDownProps) => {
        if (event.key === 'Escape') {
          close()
          return true
        }
        return renderer?.ref?.onKeyDown(event) ?? false
      },
      onExit: close,
    }
  }
}
