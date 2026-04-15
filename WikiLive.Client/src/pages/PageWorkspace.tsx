import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { JSONContent } from '@tiptap/core'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  History,
  MessageSquare,
  RefreshCw,
  RotateCcw,
  ThumbsUp,
  Trash2,
  Check,
  Pencil,
} from 'lucide-react'
import { commentsApi, isVersionConflict, pagesApi } from '@/api/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import type { Comment, Json, PageActivityItem } from '@/lib/types'
import { selectionAnchorJson, toTiptapDoc } from '@/lib/tiptap'
import { PageEditor } from '@/components/PageEditor'
import { cn } from '@/lib/utils'

export function PageWorkspace() {
  const { pageId = '' } = useParams()
  const qc = useQueryClient()
  const [title, setTitle] = useState('')
  const [editorJson, setEditorJson] = useState<JSONContent>(() => toTiptapDoc(null))
  const [dirty, setDirty] = useState(false)
  const [commentText, setCommentText] = useState('')
  const [replyTo, setReplyTo] = useState<string | null>(null)
  const [conflictOpen, setConflictOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingText, setEditingText] = useState('')

  const pageQuery = useQuery({
    queryKey: ['page', pageId],
    queryFn: () => pagesApi.get(pageId),
    enabled: !!pageId,
  })

  const activityQuery = useQuery({
    queryKey: ['activity', pageId],
    queryFn: () => pagesApi.activity(pageId),
    enabled: !!pageId,
  })

  const page = pageQuery.data

  // Sync local draft when the server page identity or document version changes (navigate / save / restore).
  useEffect(() => {
    if (!page) return
    setTitle(page.title)
    setEditorJson(toTiptapDoc(page.content as Json))
    setDirty(false)
    // Intentionally omit `page` object identity so comment-only refetches do not wipe the editor.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page?.id, page?.version])

  useEffect(() => {
    if (!dirty) return
    const current = page
    if (!current) return
    const t = window.setTimeout(async () => {
      try {
        await pagesApi.update(
          { ...current, title, content: editorJson as unknown as Json },
          current.version,
          null
        )
        setDirty(false)
        await qc.invalidateQueries({ queryKey: ['page', pageId] })
        await qc.invalidateQueries({ queryKey: ['activity', pageId] })
        await qc.invalidateQueries({ queryKey: ['pages'] })
      } catch (e) {
        if (isVersionConflict(e)) {
          setConflictOpen(true)
          await qc.invalidateQueries({ queryKey: ['page', pageId] })
        } else {
          console.error(e)
        }
      }
    }, 900)
    return () => window.clearTimeout(t)
  }, [dirty, title, editorJson, page, pageId, qc])

  const restore = useMutation({
    mutationFn: ({ vid }: { vid: string }) => pagesApi.restoreVersion(pageId, vid),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['page', pageId] })
      await qc.invalidateQueries({ queryKey: ['activity', pageId] })
      await qc.invalidateQueries({ queryKey: ['pages'] })
    },
  })

  const addComment = useMutation({
    mutationFn: (payload: { text: string; parentId?: string | null; anchor?: Json | null; selectedText?: string | null }) =>
      commentsApi.create({ pageId, text: payload.text, parentId: payload.parentId, anchor: payload.anchor, selectedText: payload.selectedText }),
    onSuccess: async () => {
      setCommentText('')
      setReplyTo(null)
      await qc.invalidateQueries({ queryKey: ['page', pageId] })
      await qc.invalidateQueries({ queryKey: ['activity', pageId] })
      await qc.invalidateQueries({ queryKey: ['pages'] })
    },
  })

  const like = useMutation({
    mutationFn: (id: string) => commentsApi.like(id),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['page', pageId] })
      await qc.invalidateQueries({ queryKey: ['activity', pageId] })
    },
  })

  const resolve = useMutation({
    mutationFn: ({ id, resolved }: { id: string; resolved: boolean }) => commentsApi.resolve(id, resolved),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['page', pageId] })
      await qc.invalidateQueries({ queryKey: ['activity', pageId] })
      await qc.invalidateQueries({ queryKey: ['pages'] })
    },
  })

  const remove = useMutation({
    mutationFn: (id: string) => commentsApi.delete(id),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['page', pageId] })
      await qc.invalidateQueries({ queryKey: ['activity', pageId] })
      await qc.invalidateQueries({ queryKey: ['pages'] })
    },
  })

  const updateComment = useMutation({
    mutationFn: ({ id, text }: { id: string; text: string }) => commentsApi.update(id, { text }),
    onSuccess: async () => {
      setEditingId(null)
      await qc.invalidateQueries({ queryKey: ['page', pageId] })
      await qc.invalidateQueries({ queryKey: ['activity', pageId] })
    },
  })

  const selectionForComment = useCallback(() => {
    const sel = window.getSelection?.()
    if (!sel || sel.rangeCount === 0) return null
    const text = sel.toString().trim()
    if (!text) return null
    return { text }
  }, [])

  const topLevelComments = useMemo(() => {
    const list = page?.comments ?? []
    return list.filter((c) => !c.parentId)
  }, [page?.comments])

  const getReplies = useCallback(
    (parentId: string) => (page?.comments ?? []).filter((c) => c.parentId === parentId),
    [page?.comments]
  )

  if (pageQuery.isLoading || !page) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        Loading…
      </div>
    )
  }

  return (
    <div className="flex min-h-screen flex-col bg-muted/30">
      {conflictOpen && (
        <div className="border-b border-destructive/40 bg-destructive/10 px-4 py-2 text-sm text-destructive">
          This page changed elsewhere before your edit saved. We refreshed the latest version — review and save again.
          <Button variant="ghost" size="sm" className="ml-2 h-7" onClick={() => setConflictOpen(false)}>
            Dismiss
          </Button>
        </div>
      )}
      <header className="border-b bg-card px-4 py-3">
        <div className="mx-auto flex max-w-[1400px] items-center gap-3">
          <Button variant="ghost" size="icon" asChild>
            <Link to="/" aria-label="Back to inbox">
              <ArrowLeft className="h-4 w-4" />
            </Link>
          </Button>
          <div className="flex flex-1 flex-col gap-1">
            <Input
              value={title}
              onChange={(e) => {
                setTitle(e.target.value)
                setDirty(true)
              }}
              className="h-9 max-w-xl border-transparent bg-transparent px-0 text-lg font-semibold shadow-none focus-visible:ring-0"
            />
            <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              <span>v{page.version}</span>
              <span>·</span>
              <span>Updated {page.updatedAt ? new Date(page.updatedAt).toLocaleString() : '—'}</span>
            </div>
          </div>
          <Sheet>
            <SheetTrigger asChild>
              <Button variant="outline" size="sm" className="gap-1">
                <History className="h-4 w-4" />
                History
              </Button>
            </SheetTrigger>
            <SheetContent side="right" className="flex flex-col">
              <SheetHeader>
                <SheetTitle>Versions & restore</SheetTitle>
                <SheetDescription>Older snapshots of this page. Restoring creates a new version.</SheetDescription>
              </SheetHeader>
              <ScrollArea className="mt-4 flex-1 pr-3">
                <div className="flex flex-col gap-2">
                  {(page.versions ?? []).map((v) => (
                    <Card key={v.id}>
                      <CardHeader className="space-y-1 py-3">
                        <CardTitle className="text-sm">Version #{v.versionNumber}</CardTitle>
                        <p className="text-xs text-muted-foreground">
                          {v.createdAt ? new Date(v.createdAt).toLocaleString() : ''}
                          {v.snapshotTitle ? ` · “${v.snapshotTitle}”` : ''}
                        </p>
                        {v.comment && <p className="text-xs text-muted-foreground">Note: {v.comment}</p>}
                      </CardHeader>
                      <CardContent className="pb-3">
                        <Button
                          size="sm"
                          variant="secondary"
                          className="gap-1"
                          disabled={restore.isPending}
                          onClick={() => v.id && restore.mutate({ vid: v.id })}
                        >
                          <RotateCcw className="h-3 w-3" />
                          Restore
                        </Button>
                      </CardContent>
                    </Card>
                  ))}
                  {(page.versions ?? []).length === 0 && (
                    <p className="text-sm text-muted-foreground">No versions yet. Edit the page to create history.</p>
                  )}
                </div>
              </ScrollArea>
            </SheetContent>
          </Sheet>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-[1400px] flex-1 gap-4 p-4">
        <section className="min-w-0 flex-1 space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="gap-1"
              onClick={() => {
                const s = selectionForComment()
                if (!s) {
                  window.alert('Select text in the page to attach an inline comment.')
                  return
                }
                setCommentText((t) => t || '')
                // anchor requires TipTap positions — approximate with text-only anchor for now
                setReplyTo('__inline__')
                // store selected text in a ref-like way via session: use commentText prefix hack — cleaner: useState anchor
              }}
            >
              Comment on selection
            </Button>
            <span className="text-xs text-muted-foreground">Tip: select text, then use the button or type a general comment on the right.</span>
          </div>
          <PageEditor
            pageKey={`${page.id}:${page.version}`}
            doc={editorJson}
            onChange={(json) => {
              setEditorJson(json)
              setDirty(true)
            }}
          />
        </section>

        <aside className="flex w-full max-w-md shrink-0 flex-col border-l bg-card pl-4">
          <Tabs defaultValue="discussion" className="flex h-[calc(100vh-6rem)] flex-col">
            <TabsList className="w-full justify-start">
              <TabsTrigger value="discussion" className="gap-1">
                <MessageSquare className="h-4 w-4" />
                Discussion
              </TabsTrigger>
              <TabsTrigger value="activity" className="gap-1">
                <RefreshCw className="h-4 w-4" />
                Activity
              </TabsTrigger>
            </TabsList>
            <TabsContent value="discussion" className="mt-3 flex min-h-0 flex-1 flex-col gap-3">
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm">New comment</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  {replyTo && replyTo !== '__inline__' && (
                    <p className="text-xs text-muted-foreground">
                      Replying to thread —{' '}
                      <Button variant="link" className="h-auto p-0 text-xs" onClick={() => setReplyTo(null)}>
                        cancel
                      </Button>
                    </p>
                  )}
                  {replyTo === '__inline__' && (
                    <p className="text-xs text-muted-foreground">
                      Inline mode: selected text will be quoted.{' '}
                      <Button variant="link" className="h-auto p-0 text-xs" onClick={() => setReplyTo(null)}>
                        cancel
                      </Button>
                    </p>
                  )}
                  <Textarea
                    rows={3}
                    placeholder="Write a message…"
                    value={commentText}
                    onChange={(e) => setCommentText(e.target.value)}
                  />
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      size="sm"
                      disabled={!commentText.trim() || addComment.isPending}
                      onClick={() => {
                        const sel = selectionForComment()
                        const inline = replyTo === '__inline__'
                        const anchor: Json | null =
                          inline && sel
                            ? (selectionAnchorJson(0, 0, sel.text) as Json)
                            : null
                        addComment.mutate({
                          text: commentText.trim(),
                          parentId: replyTo && replyTo !== '__inline__' ? replyTo : null,
                          selectedText: inline && sel ? sel.text : null,
                          anchor,
                        })
                        setReplyTo(null)
                      }}
                    >
                      Post
                    </Button>
                    <Button type="button" size="sm" variant="outline" onClick={() => setReplyTo(null)}>
                      Clear
                    </Button>
                  </div>
                </CardContent>
              </Card>
              <ScrollArea className="min-h-0 flex-1 pr-2">
                <div className="flex flex-col gap-3 pb-6">
                  {topLevelComments.map((c) => (
                    <CommentThread
                      key={c.id}
                      root={c}
                      childComments={getReplies(c.id)}
                      onReply={() => {
                        setReplyTo(c.id)
                        setCommentText('')
                      }}
                      onLike={() => like.mutate(c.id)}
                      onToggleResolve={() => resolve.mutate({ id: c.id, resolved: !c.resolved })}
                      onDelete={() => {
                        if (window.confirm('Delete this comment?')) remove.mutate(c.id)
                      }}
                      editingId={editingId}
                      editingText={editingText}
                      setEditingId={setEditingId}
                      setEditingText={setEditingText}
                      onSaveEdit={(id, text) => updateComment.mutate({ id, text })}
                    />
                  ))}
                  {topLevelComments.length === 0 && (
                    <p className="text-sm text-muted-foreground">No comments yet. Start the thread.</p>
                  )}
                </div>
              </ScrollArea>
            </TabsContent>
            <TabsContent value="activity" className="mt-3 min-h-0 flex-1">
              <ActivityList items={activityQuery.data ?? []} loading={activityQuery.isLoading} />
            </TabsContent>
          </Tabs>
        </aside>
      </div>
    </div>
  )
}

function ActivityList({ items, loading }: { items: PageActivityItem[]; loading: boolean }) {
  if (loading) return <p className="text-sm text-muted-foreground">Loading activity…</p>
  return (
    <ScrollArea className="h-[calc(100vh-10rem)] pr-2">
      <div className="flex flex-col gap-2 pb-8">
        {items.map((it, idx) => (
          <Card key={`${it.type}-${idx}`}>
            <CardHeader className="py-3">
              <div className="flex items-center justify-between gap-2">
                <Badge variant="outline">{it.type}</Badge>
                <span className="text-xs text-muted-foreground">{new Date(it.occurredAt).toLocaleString()}</span>
              </div>
              {it.comment && (
                <p className="text-sm">
                  <span className="text-muted-foreground">Comment: </span>
                  {it.comment.text}
                </p>
              )}
              {it.version && (
                <p className="text-sm text-muted-foreground">
                  Version snapshot #{it.version.versionNumber}
                  {it.version.snapshotTitle ? ` · ${it.version.snapshotTitle}` : ''}
                </p>
              )}
            </CardHeader>
          </Card>
        ))}
        {items.length === 0 && <p className="text-sm text-muted-foreground">No activity yet.</p>}
      </div>
    </ScrollArea>
  )
}

function CommentThread({
  root,
  childComments,
  onReply,
  onLike,
  onToggleResolve,
  onDelete,
  editingId,
  editingText,
  setEditingId,
  setEditingText,
  onSaveEdit,
}: {
  root: Comment
  childComments: Comment[]
  onReply: () => void
  onLike: () => void
  onToggleResolve: () => void
  onDelete: () => void
  editingId: string | null
  editingText: string
  setEditingId: (id: string | null) => void
  setEditingText: (t: string) => void
  onSaveEdit: (id: string, text: string) => void
}) {
  return (
    <Card className={cn(root.resolved && 'opacity-70')}>
      <CardHeader className="space-y-2 py-3">
        <div className="flex items-start justify-between gap-2">
          <div className="space-y-1">
            {root.selectedText && (
              <blockquote className="border-l-2 border-primary pl-2 text-xs italic text-muted-foreground">
                {root.selectedText}
              </blockquote>
            )}
            {editingId === root.id ? (
              <div className="space-y-2">
                <Textarea value={editingText} onChange={(e) => setEditingText(e.target.value)} rows={3} />
                <div className="flex gap-2">
                  <Button size="sm" onClick={() => onSaveEdit(root.id, editingText)}>
                    Save
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => setEditingId(null)}>
                    Cancel
                  </Button>
                </div>
              </div>
            ) : (
              <p className="text-sm">{root.text}</p>
            )}
          </div>
          <div className="flex shrink-0 flex-col items-end gap-1">
            {root.resolved ? <Badge variant="secondary">Resolved</Badge> : <Badge variant="outline">Open</Badge>}
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <span>{root.createdAt ? new Date(root.createdAt).toLocaleString() : ''}</span>
          <span className="inline-flex items-center gap-1">
            <ThumbsUp className="h-3 w-3" />
            {root.likes}
          </span>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" className="h-7 gap-1 text-xs" onClick={onLike}>
            <ThumbsUp className="h-3 w-3" />
            Like
          </Button>
          <Button size="sm" variant="outline" className="h-7 gap-1 text-xs" onClick={onToggleResolve}>
            <Check className="h-3 w-3" />
            {root.resolved ? 'Reopen' : 'Resolve'}
          </Button>
          <Button size="sm" variant="outline" className="h-7 gap-1 text-xs" onClick={onReply}>
            Reply
          </Button>
          <Button
            size="sm"
            variant="outline"
            className="h-7 gap-1 text-xs"
            onClick={() => {
              setEditingId(root.id)
              setEditingText(root.text)
            }}
          >
            <Pencil className="h-3 w-3" />
            Edit
          </Button>
          <Button size="sm" variant="ghost" className="h-7 gap-1 text-xs text-destructive" onClick={onDelete}>
            <Trash2 className="h-3 w-3" />
            Delete
          </Button>
        </div>
        {childComments.length > 0 && (
          <div className="mt-2 space-y-2 border-l pl-3">
            <Label className="text-xs text-muted-foreground">Replies</Label>
            {childComments.map((r) => (
              <div key={r.id} className="rounded-md bg-muted/50 p-2 text-sm">
                <p>{r.text}</p>
                <p className="mt-1 text-xs text-muted-foreground">{new Date(r.createdAt ?? '').toLocaleString()}</p>
              </div>
            ))}
          </div>
        )}
      </CardHeader>
    </Card>
  )
}
