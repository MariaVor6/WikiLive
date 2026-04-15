import type { JSONContent } from '@tiptap/core'
import type { Json } from '@/lib/types'

const emptyDoc: JSONContent = { type: 'doc', content: [{ type: 'paragraph' }] }

export function toTiptapDoc(content: Json | null | undefined): JSONContent {
  if (content == null) return emptyDoc
  if (typeof content === 'string') {
    return {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: content }] }],
    }
  }
  if (typeof content === 'object' && content !== null && !Array.isArray(content)) {
    const o = content as Record<string, Json>
    if (o.type === 'doc') return content as JSONContent
  }
  return {
    type: 'doc',
    content: [
      {
        type: 'paragraph',
        content: [{ type: 'text', text: typeof content === 'string' ? content : JSON.stringify(content) }],
      },
    ],
  }
}

export function selectionAnchorJson(from: number, to: number, text: string): Json {
  return { type: 'tiptapRange', from, to, text }
}
