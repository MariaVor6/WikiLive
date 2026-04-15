import axios, { type AxiosError } from 'axios'
import type { Comment, Json, Page, PageActivityItem, VersionConflictResponse } from '@/lib/types'

const api = axios.create({
  baseURL: '/api',
})

export { api }

export const pagesApi = {
  list: async () => {
    const res = await api.get<Page[]>('/pages')
    return res.data
  },
  get: async (id: string) => {
    const res = await api.get<Page>(`/pages/${id}`)
    return res.data
  },
  activity: async (id: string) => {
    const res = await api.get<PageActivityItem[]>(`/pages/${id}/activity`)
    return res.data
  },
  create: async (title: string, content: Json = { type: 'doc', content: [{ type: 'paragraph' }] }) => {
    const res = await api.post<Page>('/pages', { title, content })
    return res.data
  },
  update: async (page: Page, expectedVersion: number, versionSummary?: string | null) => {
    return api.put(`/pages/${page.id}?expectedVersion=${expectedVersion}`, {
      ...page,
      versionSummary: versionSummary ?? null,
    })
  },
  restoreVersion: async (pageId: string, versionId: string) => {
    const res = await api.post<Page>(`/pages/${pageId}/restore/${versionId}`)
    return res.data
  },
}

export function isVersionConflict(err: unknown): err is AxiosError<VersionConflictResponse> {
  return axios.isAxiosError(err) && err.response?.status === 409
}

export const commentsApi = {
  create: async (comment: {
    pageId: string
    text: string
    selectedText?: string | null
    anchor?: Json | null
    parentId?: string | null
  }) => {
    const res = await api.post<Comment>('/comments', {
      pageId: comment.pageId,
      text: comment.text,
      selectedText: comment.selectedText ?? null,
      anchor: comment.anchor ?? null,
      parentId: comment.parentId ?? null,
      resolved: false,
      likes: 0,
    })
    return res.data
  },
  update: async (id: string, patch: { text?: string; resolved?: boolean }) => {
    const res = await api.put<Comment>(`/comments/${id}`, patch)
    return res.data
  },
  like: async (id: string) => {
    const res = await api.post<{ likes: number }>(`/comments/${id}/like`)
    return res.data
  },
  resolve: async (id: string, resolved: boolean) => {
    await api.put(`/comments/${id}/resolve`, { resolved })
  },
  delete: async (id: string) => {
    await api.delete(`/comments/${id}`)
  },
}
