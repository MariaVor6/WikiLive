package com.wikilive.routes

import com.wikilive.db.*
import com.wikilive.models.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.DeleteStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.*

fun Route.pagesRoutes() {
    route("/pages") {
        get {
            val pages = transaction {
                Pages.selectAll().map { row ->
                    val pageId = row[Pages.id]
                    val openCount = Comments
                        .select(Comments.id.count())
                        .where { (Comments.pageId eq pageId) and (Comments.resolved eq false) }
                        .first()[Comments.id.count()]
                        .toInt()
                    val lastActivity = lastActivityAtForPage(pageId, row[Pages.updatedAt])
                    row.toPage().copy(
                        openCommentsCount = openCount,
                        lastActivityAt = lastActivity?.toString()
                    )
                }
            }
            call.respond(pages)
        }

        get("/{id}/activity") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")

            val items = transaction {
                val pageExists = Pages.selectAll().where { Pages.id eq id }.singleOrNull() != null
                if (!pageExists) return@transaction null

                val commentRows = Comments.selectAll().where { Comments.pageId eq id }
                val versionRows = PageVersions.selectAll().where { PageVersions.pageId eq id }
                    .orderBy(PageVersions.createdAt to SortOrder.DESC)

                val fromComments = commentRows.map { row ->
                    val c = row.toComment()
                    val occurred = c.updatedAt ?: c.createdAt ?: row[Comments.createdAt].toString()
                    PageActivityItem(type = "comment", occurredAt = occurred, comment = c)
                }
                val fromVersions = versionRows.map { row ->
                    val v = row.toPageVersion()
                    val occurred = v.createdAt ?: row[PageVersions.createdAt].toString()
                    PageActivityItem(type = "version", occurredAt = occurred, version = v)
                }
                (fromComments + fromVersions).sortedByDescending { it.occurredAt }
            }

            if (items == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(items)
            }
        }

        get("/{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")

            val page = transaction {
                val pageRow = Pages.selectAll().where { Pages.id eq id }.singleOrNull()
                    ?: return@transaction null

                val comments = Comments.selectAll().where { Comments.pageId eq id }
                    .map { it.toComment() }

                val versions = PageVersions.selectAll().where { PageVersions.pageId eq id }
                    .orderBy(PageVersions.versionNumber to SortOrder.DESC)
                    .map { it.toPageVersion() }

                pageRow.toPage(comments = comments, versions = versions)
            }

            if (page == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(page)
            }
        }

        post {
            val request = call.receive<Page>()
            val now = OffsetDateTime.now()
            val id = UUID.randomUUID()

            transaction {
                Pages.insert {
                    it[Pages.id] = id
                    it[title] = request.title
                    it[content] = request.content?.let { c -> Json.encodeToString(JsonElement.serializer(), c) }
                    it[spaceId] = request.spaceId
                    it[createdAt] = now
                    it[updatedAt] = now
                    it[createdBy] = request.createdBy
                    it[updatedBy] = request.updatedBy
                    it[version] = 1
                }
            }

            val createdPage = transaction {
                Pages.selectAll().where { Pages.id eq id }.single().toPage()
            }
            call.respond(HttpStatusCode.Created, createdPage)
        }

        put("/{id}") {
            val idParam = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid ID")
            val request = call.receive<Page>()

            if (idParam.toString() != request.id) {
                return@put call.respond(HttpStatusCode.BadRequest, "ID mismatch")
            }

            val expectedVersion = call.request.queryParameters["expectedVersion"]?.toIntOrNull()

            val now = OffsetDateTime.now()

            val result = transaction {
                val existing = Pages.selectAll().where { Pages.id eq idParam }.singleOrNull()
                    ?: return@transaction UpdatePageResult.NotFound

                val currentVersion = existing[Pages.version]
                if (expectedVersion != null && expectedVersion != currentVersion) {
                    return@transaction UpdatePageResult.VersionConflict(currentVersion)
                }

                val existingVersion = existing[Pages.version]
                val existingContent = existing[Pages.content]

                PageVersions.insert {
                    it[PageVersions.id] = UUID.randomUUID()
                    it[PageVersions.pageId] = idParam
                    it[PageVersions.versionNumber] = existingVersion
                    it[PageVersions.content] = existingContent
                    it[PageVersions.snapshotTitle] = existing[Pages.title]
                    it[PageVersions.createdAt] = now
                    it[PageVersions.createdBy] = request.updatedBy
                    it[PageVersions.comment] = request.versionSummary
                }

                Pages.update({ Pages.id eq idParam }) {
                    it[title] = request.title
                    it[content] = request.content?.let { c -> Json.encodeToString(JsonElement.serializer(), c) }
                    it[updatedAt] = now
                    it[updatedBy] = request.updatedBy
                    it[version] = existingVersion + 1
                }
                UpdatePageResult.Ok
            }

            when (result) {
                UpdatePageResult.NotFound -> call.respond(HttpStatusCode.NotFound)
                is UpdatePageResult.VersionConflict -> call.respond(
                    HttpStatusCode.Conflict,
                    VersionConflictResponse(
                        message = "Version mismatch",
                        currentVersion = result.current
                    )
                )
                UpdatePageResult.Ok -> call.respond(HttpStatusCode.NoContent)
            }
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid ID")

            val deleted = transaction {
                val count = DeleteStatement(
                    Pages,
                    EqOp(Pages.id, QueryParameter(id, Pages.id.columnType)),
                    false,
                    null,
                    null
                ).execute(this)
                (count ?: 0) > 0
            }

            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        post("/{id}/restore/{versionId}") {
            val pageId = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid page ID")
            val versionId = call.parameters["versionId"]?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid version ID")

            val now = OffsetDateTime.now()
            val restoredPage = transaction {
                val pageRow = Pages.selectAll().where { Pages.id eq pageId }.singleOrNull()
                val versionRow = PageVersions.selectAll().where { PageVersions.id eq versionId }.singleOrNull()

                if (pageRow == null || versionRow == null) {
                    return@transaction null
                }

                val versionContent = versionRow[PageVersions.content]
                val currentVersion = pageRow[Pages.version]

                Pages.update({ Pages.id eq pageId }) {
                    it[content] = versionContent
                    it[updatedAt] = now
                    it[version] = currentVersion + 1
                }

                Pages.selectAll().where { Pages.id eq pageId }.single().toPage()
            }

            if (restoredPage == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(restoredPage)
            }
        }
    }
}

private sealed class UpdatePageResult {
    data object NotFound : UpdatePageResult()
    data object Ok : UpdatePageResult()
    data class VersionConflict(val current: Int) : UpdatePageResult()
}

private fun lastActivityAtForPage(pageId: UUID, pageUpdatedAt: OffsetDateTime): OffsetDateTime {
    val commentTimes = Comments.select(Comments.createdAt, Comments.updatedAt)
        .where { Comments.pageId eq pageId }
        .map { row ->
            val created = row[Comments.createdAt]
            val updated = row[Comments.updatedAt]
            if (updated > created) updated else created
        }
    return (sequenceOf(pageUpdatedAt) + commentTimes.asSequence()).maxOrNull() ?: pageUpdatedAt
}

fun ResultRow.toPage(comments: List<Comment> = emptyList(), versions: List<PageVersion> = emptyList()): Page = Page(
    id = this[Pages.id].toString(),
    title = this[Pages.title],
    content = this[Pages.content]?.let { Json.parseToJsonElement(it) },
    spaceId = this[Pages.spaceId],
    createdAt = this[Pages.createdAt].toString(),
    updatedAt = this[Pages.updatedAt].toString(),
    createdBy = this[Pages.createdBy],
    updatedBy = this[Pages.updatedBy],
    version = this[Pages.version],
    comments = comments,
    versions = versions
)

fun ResultRow.toPageVersion(): PageVersion = PageVersion(
    id = this[PageVersions.id].toString(),
    pageId = this[PageVersions.pageId].toString(),
    versionNumber = this[PageVersions.versionNumber],
    content = this[PageVersions.content]?.let { Json.parseToJsonElement(it) },
    snapshotTitle = this[PageVersions.snapshotTitle],
    createdAt = this[PageVersions.createdAt].toString(),
    createdBy = this[PageVersions.createdBy],
    comment = this[PageVersions.comment]
)
