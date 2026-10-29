import { Extension } from '@tiptap/react'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet, type EditorView } from '@tiptap/pm/view'
import { WIKI_LINK } from '../markdown'

export type WikiLinksOptions = {
  /** A nota com esse título existe (link normal) ou não (link para criar). */
  exists: (title: string) => boolean
  onOpen: (title: string) => void
}

const key = new PluginKey<DecorationSet>('wikiLinks')

/** Destaca `[[Nome]]` sem mudar o texto (o markdown continua igual); Ctrl/Cmd + clique (ou clique na leitura) abre. */
export const WikiLinks = Extension.create<WikiLinksOptions>({
  name: 'wikiLinks',

  addOptions() {
    return { exists: () => true, onOpen: () => undefined }
  },

  addProseMirrorPlugins() {
    const options = this.options
    return [
      new Plugin<DecorationSet>({
        key,
        state: {
          init: (_, state) => decorate(state.doc, options),
          apply: (tr, old) => (tr.docChanged || tr.getMeta(key) ? decorate(tr.doc, options) : old),
        },
        props: {
          decorations: (state) => key.getState(state),
          handleClick: (view, _pos, event) => {
            const target = (event.target as HTMLElement | null)?.closest<HTMLElement>('[data-wiki]')
            if (!target || (view.editable && !event.ctrlKey && !event.metaKey)) return false
            options.onOpen(target.dataset.wiki ?? '')
            return true
          },
        },
      }),
    ]
  },
})

/** Redesenha os destaques (ex.: a lista de notas carregou depois do editor). */
export function refreshWikiLinks(view: EditorView) {
  view.dispatch(view.state.tr.setMeta(key, true))
}

function decorate(doc: ProseMirrorNode, options: WikiLinksOptions) {
  const decorations: Decoration[] = []
  doc.descendants((node, pos) => {
    if (!node.isText || !node.text || node.marks.some((m) => m.type.name === 'code')) return
    for (const match of node.text.matchAll(WIKI_LINK)) {
      const title = match[1].trim()
      const from = pos + (match.index ?? 0)
      decorations.push(Decoration.inline(from, from + match[0].length, {
        class: options.exists(title) ? 'note-wiki-link' : 'note-wiki-link note-wiki-link-missing',
        'data-wiki': title,
        title: options.exists(title) ? `Abrir "${title}" (Ctrl + clique)` : `Criar "${title}" (Ctrl + clique)`,
      }))
    }
  })
  return DecorationSet.create(doc, decorations)
}
