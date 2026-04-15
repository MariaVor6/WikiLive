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
import org.jetbrains.exposed.sql.statements.DeleteStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.*

fun ResultRow.toComment(): Comment {
    val created = this[Comments.createdAt].toString()
    val updatedRaw = this[Comments.updatedAt]
    val updated = updatedRaw?.toString()
    return Comment(
        id = this[Comments.id].toString(),
        pageId = this[Comments.pageId].toString(),
        selectedText = this[Comments.selectedText],
        anchor = this[Comments.anchor]?.let { Json.parseToJsonElement(it) },
        text = this[Comments.text],
        resolved = this[Comments.resolved],
        createdAt = created,
        updatedAt = updated,
        createdBy = this[Comments.createdBy],
        parentId = this[Comments.parentId]?.toString(),
        likes = this[Comments.likes]
    )
}

fun Route.commentsRoutes() {
    route("/comments") {
        post {
            val request = call.receive<Comment>()
            val now = OffsetDateTime.now()
            val id = UUID.randomUUID()

            transaction {
                Comments.insert {
                    it[Comments.id] = id
                    it[Comments.pageId] = UUID.fromString(request.pageId!!)
                    it[Comments.selectedText] = request.selectedText
                    it[Comments.anchor] =
                        request.anchor?.let { a -> Json.encodeToString(JsonElement.serializer(), a) }
                    it[Comments.text] = request.text
                    it[Comments.resolved] = request.resolved
                    it[Comments.createdAt] = now
                    it[Comments.updatedAt] = now
                    it[Comments.createdBy] = request.createdBy
                    it[Comments.parentId] = request.parentId?.let { UUID.fromString(it) }
                    it[Comments.likes] = request.likes
                }
            }

            val created = transaction {
                Comments.selectAll().where { Comments.id eq id }.single().toComment()
            }
            call.respond(created)
        }

        put("/{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid ID")
            val body = call.receive<CommentUpdate>()
            val now = OffsetDateTime.now()

            val updated = transaction {
                val exists = Comments.selectAll().where { Comments.id eq id }.singleOrNull()
                    ?: return@transaction false
                Comments.update({ Comments.id eq id }) {
                    if (body.text != null) {
                        it[Comments.text] = body.text!!
                    }
                    if (body.resolved != null) {
                        it[Comments.resolved] = body.resolved!!
                    }
                    it[Comments.updatedAt] = now
                } > 0
            }

            if (!updated) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val comment = transaction {
                    Comments.selectAll().where { Comments.id eq id }.single().toComment()
                }
                call.respond(comment)
            }
        }

        post("/{id}/like") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid ID")
            val now = OffsetDateTime.now()

            val likes = transaction {
                val comment = Comments.selectAll().where { Comments.id eq id }.singleOrNull()
                    ?: return@transaction null
                val newLikes = comment[Comments.likes] + 1
                Comments.update({ Comments.id eq id }) {
                    it[Comments.likes] = newLikes
                    it[Comments.updatedAt] = now
                }
                newLikes
            }

            if (likes == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(mapOf("likes" to likes))
            }
        }

        put("/{id}/resolve") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid ID")
            val body = call.receive<CommentResolveBody>()
            val now = OffsetDateTime.now()

            val updated = transaction {
                Comments.update({ Comments.id eq id }) {
                    it[Comments.resolved] = body.resolved
                    it[Comments.updatedAt] = now
                } > 0
            }

            if (updated) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid ID")

            val deleted = transaction {
                val count = DeleteStatement(
                    Comments,
                    EqOp(Comments.id, QueryParameter(id, Comments.id.columnType)),
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
    }
}
