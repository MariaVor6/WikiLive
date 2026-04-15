import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { MessageSquare, Plus } from 'lucide-react'
import { pagesApi } from '@/api/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { useState } from 'react'

export function PageInbox() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [newTitle, setNewTitle] = useState('')
  const { data: pages = [], isLoading } = useQuery({
    queryKey: ['pages'],
    queryFn: pagesApi.list,
  })

  const create = useMutation({
    mutationFn: () => pagesApi.create(newTitle.trim() || 'Untitled'),
    onSuccess: async (p) => {
      setNewTitle('')
      await qc.invalidateQueries({ queryKey: ['pages'] })
      navigate(`/pages/${p.id}`)
    },
  })

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b bg-card px-6 py-4">
        <div className="mx-auto flex max-w-4xl items-center justify-between gap-4">
          <div>
            <h1 className="text-xl font-semibold">WikiLive</h1>
            <p className="text-sm text-muted-foreground">Pages and open discussions</p>
          </div>
          <div className="flex max-w-md flex-1 items-center gap-2">
            <Input
              placeholder="New page title…"
              value={newTitle}
              onChange={(e) => setNewTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') create.mutate()
              }}
            />
            <Button type="button" onClick={() => create.mutate()} disabled={create.isPending}>
              <Plus className="h-4 w-4" />
              New
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-4xl flex-1 p-6">
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading pages…</p>
        ) : (
          <ScrollArea className="h-[calc(100vh-8rem)] pr-3">
            <div className="flex flex-col gap-3">
              {pages.map((p) => (
                <Link key={p.id} to={`/pages/${p.id}`}>
                  <Card className="transition-colors hover:bg-accent/40">
                    <CardHeader className="py-3">
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <CardTitle className="text-base">{p.title}</CardTitle>
                          <CardDescription className="mt-1 flex flex-wrap items-center gap-2 text-xs">
                            {p.lastActivityAt && (
                              <span>Last activity: {new Date(p.lastActivityAt).toLocaleString()}</span>
                            )}
                          </CardDescription>
                        </div>
                        <div className="flex shrink-0 items-center gap-2">
                          {typeof p.openCommentsCount === 'number' && p.openCommentsCount > 0 ? (
                            <Badge variant="secondary" className="gap-1">
                              <MessageSquare className="h-3 w-3" />
                              {p.openCommentsCount} open
                            </Badge>
                          ) : (
                            <Badge variant="outline">No open threads</Badge>
                          )}
                        </div>
                      </div>
                    </CardHeader>
                  </Card>
                </Link>
              ))}
              {pages.length === 0 && <p className="text-sm text-muted-foreground">No pages yet. Create one above.</p>}
            </div>
            <Separator className="my-6" />
          </ScrollArea>
        )}
      </main>
    </div>
  )
}
