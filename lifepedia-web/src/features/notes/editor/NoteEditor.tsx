import { EditorContent, useEditor, type Editor } from '@tiptap/react'
import { CodeIcon, Columns2Icon, EyeIcon } from 'lucide-react'
import { useEffect, useLayoutEffect, useMemo, useRef, useState, type ChangeEvent, type RefObject } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { toggleTask, unescapeWikiLinks } from '../markdown'
import type { NoteRef } from '../types'
import { noteExtensions, type EditorHooks } from './extensions'
import { Toolbar } from './Toolbar'
import { refreshWikiLinks } from './WikiLinks'

export type EditorMode = 'visual' | 'markdown' | 'split'

const MODE_KEY = 'lifepedia.notes.mode'
const MODES: { mode: EditorMode; label: string; icon: typeof EyeIcon; desktopOnly?: boolean }[] = [
  { mode: 'visual', label: 'Visual', icon: EyeIcon },
  { mode: 'markdown', label: 'Markdown', icon: CodeIcon },
  { mode: 'split', label: 'Lado a lado', icon: Columns2Icon, desktopOnly: true },
]

type Props = {
  value: string
  onChange: (markdown: string) => void
  readOnly?: boolean
  refs: NoteRef[]
  onOpenLink: (title: string) => void
  /** Envia a foto e devolve a URL. */
  onUploadImage: (file: File) => Promise<string>
}

/** Editor da nota: visual (grava markdown), markdown puro ou os dois lado a lado; modo lembrado no aparelho. */
export function NoteEditor({ value, onChange, readOnly = false, refs, onOpenLink, onUploadImage }: Props) {
  const [mode, setMode] = useState<EditorMode>(readMode)
  const fileInput = useRef<HTMLInputElement>(null)
  const textarea = useRef<HTMLTextAreaElement>(null)
  const visual = useRef<Editor | null>(null)
  const latest = useRef({ refs, onOpenLink, onUploadImage, mode, value, onChange })
  // Antes dos efeitos dos filhos (o editor redesenha os links com a lista nova).
  useLayoutEffect(() => {
    latest.current = { refs, onOpenLink, onUploadImage, mode, value, onChange }
  })

  const hooks = useMemo<EditorHooks>(() => ({
    refs: () => latest.current.refs,
    exists: (title) => latest.current.refs.some((r) => r.title.toLowerCase() === title.toLowerCase()),
    onOpenLink: (title) => latest.current.onOpenLink(title),
    onImage: () => fileInput.current?.click(),
  }), [])

  function changeMode(next: EditorMode) {
    setMode(next)
    try {
      localStorage.setItem(MODE_KEY, next)
    } catch {
      // sem armazenamento: vale só nesta tela
    }
  }

  async function upload(file: File) {
    const id = toast.loading('Enviando foto…')
    try {
      const url = await latest.current.onUploadImage(file)
      toast.dismiss(id)
      if (latest.current.mode === 'visual' && visual.current) {
        visual.current.chain().focus().setImage({ src: url, alt: 'foto' }).run()
      } else {
        insertAtCursor(textarea, latest.current.value, `![foto](${url})`, latest.current.onChange)
      }
    } catch (error) {
      toast.error(error instanceof ApiError || error instanceof Error ? error.message : 'Não foi possível enviar a foto.', { id })
    }
  }

  function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (file) void upload(file)
  }

  const effective: EditorMode = readOnly ? 'visual' : mode

  return (
    <div className="space-y-2">
      {!readOnly && (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div role="radiogroup" aria-label="Como ver a nota" className="flex rounded-full bg-muted p-0.5">
            {MODES.map((m) => (
              <button key={m.mode} type="button" role="radio" aria-checked={mode === m.mode} onClick={() => changeMode(m.mode)}
                className={cn('flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium text-muted-foreground transition-colors motion-reduce:transition-none',
                  mode === m.mode && 'bg-background text-foreground shadow-sm', m.desktopOnly && 'hidden lg:flex')}>
                <m.icon className="size-3.5" aria-hidden /> {m.label}
              </button>
            ))}
          </div>
          {mode !== 'visual' && (
            <Button type="button" variant="ghost" size="sm" onClick={() => fileInput.current?.click()}>Inserir imagem</Button>
          )}
        </div>
      )}
      <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp" className="hidden" onChange={onFile} />

      {effective === 'visual' && (
        <VisualEditor value={value} onChange={onChange} editable={!readOnly} hooks={hooks} refs={refs} editorRef={visual}
          onPasteImage={(file) => void upload(file)} />
      )}
      {effective === 'markdown' && <MarkdownField value={value} onChange={onChange} textareaRef={textarea} />}
      {effective === 'split' && (
        <div className="grid gap-4 lg:grid-cols-2">
          <MarkdownField value={value} onChange={onChange} textareaRef={textarea} />
          <div className="hidden rounded-xl border bg-card p-4 lg:block">
            <Preview value={value} hooks={hooks} refs={refs} onToggle={(index) => onChange(toggleTask(value, index))} />
          </div>
        </div>
      )}
    </div>
  )
}

function VisualEditor({ value, onChange, editable, hooks, refs, editorRef, onPasteImage }: {
  value: string
  onChange: (markdown: string) => void
  editable: boolean
  hooks: EditorHooks
  refs: NoteRef[]
  editorRef: RefObject<Editor | null>
  onPasteImage: (file: File) => void
}) {
  const emitted = useRef(value)
  const callbacks = useRef({ onChange, onPasteImage })
  useEffect(() => {
    callbacks.current = { onChange, onPasteImage }
  })

  const editor = useEditor({
    extensions: noteExtensions(hooks),
    content: value,
    contentType: 'markdown',
    editable,
    immediatelyRender: true,
    editorProps: {
      attributes: { class: 'note-content min-h-[50vh] py-2', 'aria-label': 'Conteúdo da nota' },
      handlePaste: (_view, event) => {
        const files = [...(event.clipboardData?.files ?? [])].filter((f) => f.type.startsWith('image/'))
        if (files.length === 0) return false
        files.forEach((f) => callbacks.current.onPasteImage(f))
        return true
      },
    },
    onUpdate: ({ editor: e }) => {
      const markdown = unescapeWikiLinks(e.getMarkdown())
      emitted.current = markdown
      callbacks.current.onChange(markdown)
    },
  })

  useEffect(() => {
    editorRef.current = editor
    return () => {
      editorRef.current = null
    }
  }, [editor, editorRef])

  useEffect(() => {
    if (!editor || value === emitted.current) return
    emitted.current = value
    editor.commands.setContent(value, { contentType: 'markdown', emitUpdate: false })
  }, [editor, value])

  useEffect(() => {
    if (editor && editor.isEditable !== editable) editor.setEditable(editable, false)
  }, [editor, editable])

  useEffect(() => {
    if (editor && !editor.isDestroyed) refreshWikiLinks(editor.view)
  }, [editor, refs])

  if (!editor) return null
  return (
    <div>
      {editable && (
        <div className="sticky top-12 z-10 -mx-1 border-b bg-background/95 px-1 backdrop-blur">
          <Toolbar editor={editor} onImage={hooks.onImage} />
        </div>
      )}
      <EditorContent editor={editor} />
    </div>
  )
}

/** Leitura do markdown (lado a lado); a checklist marca direto no texto. */
function Preview({ value, hooks, refs, onToggle }: {
  value: string
  hooks: EditorHooks
  refs: NoteRef[]
  onToggle: (index: number) => void
}) {
  const readOnlyHooks = useMemo<EditorHooks>(() => ({ ...hooks, onReadOnlyChecked: () => false }), [hooks])
  const editor = useEditor({
    extensions: noteExtensions(readOnlyHooks),
    content: value,
    contentType: 'markdown',
    editable: false,
    immediatelyRender: true,
    editorProps: { attributes: { class: 'note-content', 'aria-label': 'Prévia da nota' } },
  })

  useEffect(() => {
    editor?.commands.setContent(value, { contentType: 'markdown', emitUpdate: false })
  }, [editor, value])

  useEffect(() => {
    if (editor && !editor.isDestroyed) refreshWikiLinks(editor.view)
  }, [editor, refs])

  const wrapper = useRef<HTMLDivElement>(null)
  const toggle = useRef(onToggle)
  useEffect(() => {
    toggle.current = onToggle
  })

  // O checkbox é criado pelo editor (fora do React): o onChange do React não chega, então escuta o evento nativo.
  useEffect(() => {
    const element = wrapper.current
    if (!element) return
    const onChange = (event: Event) => {
      const target = event.target as HTMLInputElement
      if (target.type !== 'checkbox') return
      const boxes = [...element.querySelectorAll<HTMLInputElement>('ul[data-type="taskList"] > li > label input[type="checkbox"]')]
      const index = boxes.indexOf(target)
      if (index >= 0) toggle.current(index)
    }
    element.addEventListener('change', onChange)
    return () => element.removeEventListener('change', onChange)
  }, [])

  return <div ref={wrapper}><EditorContent editor={editor} /></div>
}

function MarkdownField({ value, onChange, textareaRef }: {
  value: string
  onChange: (markdown: string) => void
  textareaRef: RefObject<HTMLTextAreaElement | null>
}) {
  return (
    <Textarea ref={textareaRef} value={value} onChange={(e) => onChange(e.target.value)} spellCheck
      aria-label="Markdown da nota" placeholder="# Título&#10;&#10;Escreva em markdown…"
      className="min-h-[50vh] resize-y font-mono text-base leading-6 [field-sizing:content] md:text-sm" />
  )
}

function readMode(): EditorMode {
  try {
    const saved = localStorage.getItem(MODE_KEY)
    if (saved === 'split') return window.matchMedia('(min-width: 1024px)').matches ? 'split' : 'markdown'
    return saved === 'markdown' ? saved : 'visual'
  } catch {
    return 'visual'
  }
}

function insertAtCursor(ref: RefObject<HTMLTextAreaElement | null>, value: string, text: string, onChange: (v: string) => void) {
  const element = ref.current
  const start = element?.selectionStart ?? value.length
  const end = element?.selectionEnd ?? value.length
  const before = value.slice(0, start)
  const glue = before && !before.endsWith('\n') ? '\n\n' : ''
  onChange(`${before}${glue}${text}\n${value.slice(end)}`)
}
