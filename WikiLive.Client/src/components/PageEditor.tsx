import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import type { JSONContent } from '@tiptap/core'
import { useEffect } from 'react'

interface PageEditorProps {
  doc: JSONContent
  pageKey: string
  onChange: (json: JSONContent) => void
}

export function PageEditor({ doc, pageKey, onChange }: PageEditorProps) {
  const editor = useEditor({
    extensions: [StarterKit],
    content: doc,
    editorProps: {
      attributes: {
        class: 'tiptap-editor outline-none min-h-[280px] px-1',
      },
    },
    onUpdate: ({ editor }) => {
      onChange(editor.getJSON())
    },
  })

  useEffect(() => {
    if (!editor) return
    const cur = JSON.stringify(editor.getJSON())
    const next = JSON.stringify(doc)
    if (cur !== next) {
      editor.commands.setContent(doc, { emitUpdate: false })
    }
  }, [doc, editor, pageKey])

  if (!editor) {
    return <div className="text-sm text-muted-foreground">Loading editor…</div>
  }

  return (
    <div className="rounded-md border border-input bg-background p-3">
      <EditorContent editor={editor} />
    </div>
  )
}
