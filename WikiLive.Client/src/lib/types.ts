export type Json = string | number | boolean | null | { [key: string]: Json } | Json[]

export interface PageVersion {
  id: string
  pageId?: string
  versionNumber: number
  content?: Json
  snapshotTitle?: string | null
  createdAt?: string
  createdBy?: string | null
  comment?: string | null
}

export interface Comment {
  id: string
  pageId: string
  selectedText?: string | null
  anchor?: Json | null
  text: string
  resolved: boolean
  createdAt?: string
  updatedAt?: string | null
  createdBy?: string | null
  parentId?: string | null
  likes: number
}

export interface Page {
  id: string
  title: string
  content?: Json | null
  spaceId?: string | null
  createdAt?: string
  updatedAt?: string
  createdBy?: string | null
  updatedBy?: string | null
  version: number
  versionSummary?: string | null
  openCommentsCount?: number | null
  lastActivityAt?: string | null
  comments?: Comment[]
  versions?: PageVersion[]
}

export interface PageActivityItem {
  type: 'comment' | 'version' | string
  occurredAt: string
  comment?: Comment | null
  version?: PageVersion | null
}

export interface VersionConflictResponse {
  message: string
  currentVersion: number
}
