import { CodeBlockLowlight } from '@tiptap/extension-code-block-lowlight'
import Image from '@tiptap/extension-image'
import { TaskItem, TaskList } from '@tiptap/extension-list'
import { TableKit } from '@tiptap/extension-table'
import { Placeholder } from '@tiptap/extensions'
import { Markdown } from '@tiptap/markdown'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import { mergeAttributes, type Extensions } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import { common, createLowlight } from 'lowlight'
import { isNoteImage } from '../markdown'
import type { NoteRef } from '../types'
import { SlashCommand, WikiSuggest } from './suggestions'
import { WikiLinks } from './WikiLinks'

const lowlight = createLowlight(common)

/** Só fotos enviadas pela API; qualquer outro endereço vira imagem vazia (o app nunca carrega recurso de fora). */
const NoteImage = Image.extend({
  renderHTML({ HTMLAttributes }) {
    const src = isNoteImage(HTMLAttributes.src) ? HTMLAttributes.src : ''
    return ['img', mergeAttributes(this.options.HTMLAttributes, { ...HTMLAttributes, src })]
  },
}).configure({ allowBase64: false, HTMLAttributes: { loading: 'lazy' } })

export type EditorHooks = {
  placeholder?: string
  refs: () => NoteRef[]
  exists: (title: string) => boolean
  onOpenLink: (title: string) => void
  onImage: () => void
  /** Leitura (prévia/lixeira): clicar na checklist. */
  onReadOnlyChecked?: (node: ProseMirrorNode, checked: boolean) => boolean
}

/** Extensões do editor; o conteúdo entra e sai em markdown (sem HTML solto, sem sublinhado). */
export function noteExtensions(hooks: EditorHooks): Extensions {
  return [
    StarterKit.configure({
      codeBlock: false,
      underline: false,
      heading: { levels: [1, 2, 3] },
      link: {
        openOnClick: false,
        autolink: true,
        defaultProtocol: 'https',
        isAllowedUri: (url, { defaultValidate }) => /^(https?:\/\/|mailto:)/i.test(url) && defaultValidate(url),
        HTMLAttributes: { rel: 'noopener noreferrer', target: '_blank' },
      },
    }),
    TaskList,
    TaskItem.configure({
      nested: true,
      onReadOnlyChecked: hooks.onReadOnlyChecked,
      a11y: { checkboxLabel: (node, checked) => `${checked ? 'Feito' : 'A fazer'}: ${node.textContent || 'item vazio'}` },
    }),
    TableKit.configure({ table: { resizable: false } }),
    CodeBlockLowlight.configure({ lowlight, defaultLanguage: null }),
    NoteImage,
    Placeholder.configure({ placeholder: hooks.placeholder ?? 'Escreva aqui… digite / para ver os blocos' }),
    WikiLinks.configure({ exists: hooks.exists, onOpen: hooks.onOpenLink }),
    WikiSuggest.configure({ refs: hooks.refs }),
    SlashCommand.configure({ onImage: hooks.onImage }),
    Markdown.configure({ markedOptions: { gfm: true } }),
  ]
}
