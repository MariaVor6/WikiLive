package com.wikilive.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Page(
    val id: String? = null,
    val title: String,
    val content: JsonElement? = null,
    val spaceId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val version: Int = 1,
    /** Optional: set on PUT to snapshot this text into the previous version row. */
    val versionSummary: String? = null,
    /** Populated on GET /api/pages list only. */
    val openCommentsCount: Int? = null,
    /** Populated on GET /api/pages list only. */
    val lastActivityAt: String? = null,
    val comments: List<Comment> = emptyList(),
    val versions: List<PageVersion> = emptyList()
)

@Serializable
data class PageVersion(
    val id: String? = null,
    val pageId: String? = null,
    val versionNumber: Int,
    val content: JsonElement? = null,
    val snapshotTitle: String? = null,
    val createdAt: String? = null,
    val createdBy: String? = null,
    val comment: String? = null
)

@Serializable
data class Comment(
    val id: String? = null,
    val pageId: String? = null,
    val selectedText: String? = null,
    val anchor: JsonElement? = null,
    val text: String,
    val resolved: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val createdBy: String? = null,
    val parentId: String? = null,
    val likes: Int = 0
)

@Serializable
data class CommentUpdate(
    val text: String? = null,
    val resolved: Boolean? = null
)

@Serializable
data class CommentResolveBody(
    val resolved: Boolean
)

@Serializable
data class PageActivityItem(
    val type: String,
    val occurredAt: String,
    val comment: Comment? = null,
    val version: PageVersion? = null
)

@Serializable
data class VersionConflictResponse(
    val message: String,
    val currentVersion: Int
)
